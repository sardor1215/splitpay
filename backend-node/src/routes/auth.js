const express = require('express');
const bcrypt  = require('bcryptjs');
const { v4: uuidv4 } = require('uuid');
const https   = require('https');
const { query } = require('../config/db');
const { generateAccessToken, generateRefreshToken, validateRefreshToken } = require('../config/jwt');

const router = express.Router();

function isValidPassword(password) {
  return password.length >= 8 && /[A-Z]/.test(password) && /[0-9]/.test(password);
}

// POST /auth/register
router.post('/register', async (req, res) => {
  const { name, email, password, phone } = req.body;
  if (!name?.trim() || !email?.trim() || !password)
    return res.status(400).json({ message: 'Name, email and password are required' });
  if (!isValidPassword(password))
    return res.status(400).json({ message: 'Password must be at least 8 characters, include 1 uppercase letter and 1 digit' });

  const existing = await query('SELECT id FROM users WHERE email = $1 AND is_deleted = false', [email]);
  if (existing.rows.length) return res.status(409).json({ message: 'Email already in use' });

  const hash = await bcrypt.hash(password, 12);
  await query(
    'INSERT INTO users (id, name, email, phone, password_hash, is_verified, created_at) VALUES ($1,$2,$3,$4,$5,true,NOW())',
    [uuidv4(), name, email, phone || null, hash]
  );
  res.status(201).json({ message: 'Registered successfully! You can now log in.' });
});

// POST /auth/login
router.post('/login', async (req, res) => {
  const { email, password } = req.body;
  const identifier = email?.trim();
  const result = await query(
    'SELECT * FROM users WHERE (email = $1 OR phone = $1) AND is_deleted = false',
    [identifier]
  );
  const user = result.rows[0];

  if (!user || !user.password_hash || !(await bcrypt.compare(password, user.password_hash)))
    return res.status(401).json({ message: 'Invalid credentials' });
  if (!user.is_verified)
    return res.status(403).json({ message: 'Please verify your email first' });
  if (user.aml_status === 'suspended')
    return res.status(403).json({ message: 'Your account has been suspended due to suspicious activity. Please contact support.' });

  const accessToken  = generateAccessToken(user.id, user.email);
  const refreshToken = generateRefreshToken(user.id);
  await query('UPDATE users SET refresh_token = $1 WHERE id = $2', [refreshToken, user.id]);
  res.json({ accessToken, refreshToken, userId: user.id, name: user.name, email: user.email });
});

// POST /auth/refresh
router.post('/refresh', async (req, res) => {
  const { refreshToken } = req.body;
  const userId = validateRefreshToken(refreshToken);
  if (!userId) return res.status(401).json({ message: 'Invalid or expired refresh token' });

  const result = await query('SELECT * FROM users WHERE id = $1 AND is_deleted = false', [userId]);
  const user = result.rows[0];
  if (!user) return res.status(401).json({ message: 'User not found' });
  if (user.refresh_token !== refreshToken) return res.status(401).json({ message: 'Refresh token has been revoked' });
  if (user.aml_status === 'suspended') return res.status(403).json({ message: 'Your account has been suspended.' });

  const newAccess  = generateAccessToken(user.id, user.email);
  const newRefresh = generateRefreshToken(user.id);
  await query('UPDATE users SET refresh_token = $1 WHERE id = $2', [newRefresh, user.id]);
  res.json({ accessToken: newAccess, refreshToken: newRefresh, userId: user.id, name: user.name, email: user.email });
});

// GET /auth/verify?token=
router.get('/verify', async (req, res) => {
  const { token } = req.query;
  if (!token) return res.status(400).json({ message: 'Missing token' });
  const result = await query(
    'UPDATE users SET is_verified = true, verification_token = NULL WHERE verification_token = $1', [token]
  );
  if (result.rowCount > 0) res.json({ message: 'Email verified! You can now log in.' });
  else res.status(400).json({ message: 'Invalid or expired token' });
});

// POST /auth/forgot-password
router.post('/forgot-password', async (req, res) => {
  const { email } = req.body;
  const result = await query('SELECT id FROM users WHERE email = $1 AND is_deleted = false', [email]);
  const user = result.rows[0];
  if (user) {
    const token     = uuidv4();
    const expiresAt = new Date(Date.now() + 3600_000);
    await query('UPDATE users SET password_reset_token=$1, password_reset_expires_at=$2 WHERE id=$3',
      [token, expiresAt, user.id]);
    console.log('================================================');
    console.log(`  PASSWORD RESET LINK FOR: ${email}`);
    console.log(`  ${process.env.BASE_URL || 'http://localhost:3000'}/auth/reset-password?token=${token}`);
    console.log(`  Expires at: ${expiresAt}`);
    console.log('================================================');
  }
  res.json({ message: 'If that email exists, a reset link has been sent.' });
});

// POST /auth/reset-password
router.post('/reset-password', async (req, res) => {
  const { token, newPassword } = req.body;
  if (!isValidPassword(newPassword))
    return res.status(400).json({ message: 'Password must be at least 8 characters, include 1 uppercase letter and 1 digit' });

  const result = await query(
    'SELECT id, password_reset_expires_at FROM users WHERE password_reset_token = $1', [token]
  );
  const user = result.rows[0];
  if (!user || new Date() > new Date(user.password_reset_expires_at))
    return res.status(400).json({ message: 'Invalid or expired reset token' });

  const hash = await bcrypt.hash(newPassword, 12);
  await query('UPDATE users SET password_hash=$1, password_reset_token=NULL, password_reset_expires_at=NULL WHERE id=$2',
    [hash, user.id]);
  res.json({ message: 'Password reset successfully. You can now log in.' });
});

// POST /auth/google
router.post('/google', async (req, res) => {
  const { idToken } = req.body;
  const googleUser = await verifyGoogleToken(idToken);
  if (!googleUser) return res.status(401).json({ message: 'Invalid Google token' });

  const { googleId, email, name, avatarUrl } = googleUser;
  let result = await query('SELECT * FROM users WHERE google_id = $1', [googleId]);
  let user = result.rows[0];

  if (!user) {
    result = await query('SELECT * FROM users WHERE email = $1 AND is_deleted = false', [email]);
    user = result.rows[0];
  }
  if (!user) {
    const newId = uuidv4();
    await query(
      'INSERT INTO users (id, name, email, google_id, avatar_url, is_verified, created_at) VALUES ($1,$2,$3,$4,$5,true,NOW())',
      [newId, name, email, googleId, avatarUrl || null]
    );
    result = await query('SELECT * FROM users WHERE id = $1', [newId]);
    user = result.rows[0];
  }

  const accessToken  = generateAccessToken(user.id, user.email);
  const refreshToken = generateRefreshToken(user.id);
  await query('UPDATE users SET refresh_token = $1 WHERE id = $2', [refreshToken, user.id]);
  res.json({ accessToken, refreshToken, userId: user.id, name: user.name, email: user.email });
});

// POST /auth/logout
router.post('/logout', async (req, res) => {
  try {
    const { refreshToken } = req.body;
    const userId = validateRefreshToken(refreshToken);
    if (userId) await query('UPDATE users SET refresh_token = NULL WHERE id = $1', [userId]);
  } catch {}
  res.json({ message: 'Logged out successfully' });
});

// ── Helpers ─────────────────────────────────────────────────────────────────

async function verifyGoogleToken(idToken) {
  return new Promise((resolve) => {
    https.get(`https://oauth2.googleapis.com/tokeninfo?id_token=${idToken}`, (resp) => {
      let data = '';
      resp.on('data', chunk => { data += chunk; });
      resp.on('end', () => {
        try {
          if (resp.statusCode !== 200) return resolve(null);
          const json = JSON.parse(data);
          resolve({ googleId: json.sub, email: json.email, name: json.name || 'User', avatarUrl: json.picture || null });
        } catch { resolve(null); }
      });
    }).on('error', () => resolve(null));
  });
}

module.exports = router;
