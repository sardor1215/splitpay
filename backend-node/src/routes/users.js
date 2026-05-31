const express = require('express');
const { query } = require('../config/db');
const { authenticate } = require('../middleware/auth');

const router = express.Router();
router.use(authenticate);

function toProfileResponse(u) {
  return {
    id: u.id, name: u.name, email: u.email, phone: u.phone, avatarUrl: u.avatar_url,
    preferredCurrency: u.preferred_currency, isVerified: u.is_verified, isAdmin: u.is_admin,
    kycStatus: u.kyc_status, accountBalance: parseFloat(u.account_balance || 0), requireConsent: u.require_consent,
  };
}

// GET /me
router.get('/me', async (req, res) => {
  const result = await query('SELECT * FROM users WHERE id = $1 AND is_deleted = false', [req.userId]);
  const user = result.rows[0];
  if (!user) return res.status(404).json({ message: 'User not found' });
  res.json(toProfileResponse(user));
});

// PATCH /me
router.patch('/me', async (req, res) => {
  const { name, phone, avatarUrl, preferredCurrency, requireConsent } = req.body;
  const fields = [];
  const values = [];
  let i = 1;
  if (name !== undefined)              { fields.push(`name = $${i++}`);               values.push(name); }
  if (phone !== undefined)             { fields.push(`phone = $${i++}`);              values.push(phone); }
  if (avatarUrl !== undefined)         { fields.push(`avatar_url = $${i++}`);         values.push(avatarUrl); }
  if (preferredCurrency !== undefined) { fields.push(`preferred_currency = $${i++}`); values.push(preferredCurrency); }
  if (requireConsent !== undefined)    { fields.push(`require_consent = $${i++}`);    values.push(requireConsent); }
  if (fields.length)
    await query(`UPDATE users SET ${fields.join(', ')} WHERE id = $${i}`, [...values, req.userId]);
  const result = await query('SELECT * FROM users WHERE id = $1 AND is_deleted = false', [req.userId]);
  const user = result.rows[0];
  if (!user) return res.status(404).json({ message: 'User not found' });
  res.json(toProfileResponse(user));
});

// POST /me/require-consent
router.post('/me/require-consent', async (req, res) => {
  const { value } = req.body;
  await query('UPDATE users SET require_consent = $1 WHERE id = $2', [value, req.userId]);
  res.json({ message: 'Consent preference updated' });
});

// DELETE /me
router.delete('/me', async (req, res) => {
  const id = req.userId;
  const result = await query(
    `UPDATE users SET is_deleted=true, deleted_at=NOW(), email=$1, name='Deleted User',
     phone=NULL, avatar_url=NULL, password_hash=NULL, refresh_token=NULL, google_id=NULL WHERE id=$2`,
    [`deleted_${id}@splitpay.invalid`, id]
  );
  if (result.rowCount > 0) res.json({ message: 'Account deleted. Your data has been anonymized.' });
  else res.status(500).json({ message: 'Failed to delete account' });
});

// GET /me/export/json
router.get('/me/export/json', async (req, res) => {
  const exportService = require('../services/export');
  const data = await exportService.exportUserDataJson(req.userId);
  res.setHeader('Content-Disposition', 'attachment; filename="splitpay-export.json"');
  res.setHeader('Content-Type', 'application/json');
  res.send(data);
});

// GET /me/export/pdf
router.get('/me/export/pdf', async (req, res) => {
  const exportService = require('../services/export');
  const data = await exportService.exportUserDataText(req.userId);
  res.setHeader('Content-Disposition', 'attachment; filename="splitpay-export.txt"');
  res.setHeader('Content-Type', 'text/plain');
  res.send(data);
});

// POST /users/lookup
router.post('/users/lookup', async (req, res) => {
  const { phones } = req.body;
  if (!Array.isArray(phones) || !phones.length) return res.json([]);
  const result = await query(
    'SELECT id, name, phone, email FROM users WHERE phone = ANY($1) AND is_deleted = false', [phones]
  );
  res.json(result.rows.map(u => ({ userId: u.id, name: u.name, phone: u.phone, email: u.email })));
});

// POST /users/fcm-token
// GET /me/payments — Historique des paiements de l'utilisateur
router.get('/me/payments', async (req, res) => {
  const result = await query(
    `SELECT p.id, p.amount, p.method, p.note, p.paid_at,
            p.from_user, p.to_user,
            uf.name AS from_name, ut.name AS to_name,
            s.name  AS space_name, s.id AS space_id
     FROM payments p
     LEFT JOIN users uf ON uf.id = p.from_user
     LEFT JOIN users ut ON ut.id = p.to_user
     LEFT JOIN spaces s  ON s.id  = p.space_id
     WHERE p.from_user = $1 OR p.to_user = $1
     ORDER BY p.paid_at DESC
     LIMIT 50`,
    [req.userId]
  );
  res.json(result.rows.map(p => ({
    id:         p.id,
    amount:     parseFloat(p.amount),
    method:     p.method,
    note:       p.note,
    paidAt:     p.paid_at,
    spaceName:  p.space_name,
    spaceId:    p.space_id,
    fromUserId: p.from_user,
    fromName:   p.from_name,
    toUserId:   p.to_user,
    toName:     p.to_name,
    direction:  p.from_user === req.userId ? 'sent' : 'received',
  })));
});

router.post('/users/fcm-token', async (req, res) => {
  const { token } = req.body;
  await require('../services/fcm').upsertToken(req.userId, token);
  res.json({ message: 'FCM token registered' });
});

// POST /me/pay — Direct P2P payment — AML R1–R18, KYC, frozen/suspended checks
router.post('/me/pay', async (req, res) => {
  const { toUserId, amount, note } = req.body;
  const { preCheck, checkPayment } = require('../services/aml');
  const { notifyUser } = require('../services/fcm');

  if (!toUserId) return res.status(400).json({ message: 'Recipient is required' });
  const amt = parseFloat(amount);
  if (!amt || amt <= 0) return res.status(400).json({ message: 'Amount must be positive' });
  if (toUserId === req.userId) return res.status(400).json({ message: 'Cannot pay yourself' });

  const [senderRes, recipientRes] = await Promise.all([
    query(
      'SELECT account_balance, name, aml_status, account_frozen, kyc_status FROM users WHERE id = $1 AND is_deleted = false',
      [req.userId]
    ),
    query(
      'SELECT id, name, aml_status, account_frozen FROM users WHERE id = $1 AND is_deleted = false',
      [toUserId]
    ),
  ]);
  const sender    = senderRes.rows[0];
  const recipient = recipientRes.rows[0];
  if (!recipient) return res.status(404).json({ message: 'Recipient not found' });

  // ── Compliance: sender ────────────────────────────────────────────────────
  if (sender.account_frozen)
    return res.status(403).json({ message: 'Your account is frozen. Please contact support.' });
  if (sender.aml_status === 'suspended')
    return res.status(403).json({ message: 'Your account has been suspended due to suspicious activity. Please contact support.' });

  // ── Compliance: recipient ─────────────────────────────────────────────────
  if (recipient.account_frozen || recipient.aml_status === 'suspended')
    return res.status(422).json({ message: 'Transfer blocked: recipient account has compliance restrictions.' });

  // ── Balance check ─────────────────────────────────────────────────────────
  if (parseFloat(sender.account_balance) < amt)
    return res.status(400).json({ message: `Insufficient balance. Available: €${parseFloat(sender.account_balance).toFixed(2)}` });

  // ── AML pre-check — same R1–R18 engine as group expenses ─────────────────
  // groupId=null signals P2P path; detectUnusualPattern/detectSmurfing now
  // scan all payments (not just group-scoped) when groupId is null.
  const blocked = await preCheck(req.userId, amt, null);
  if (blocked) return res.status(422).json({ message: `Payment blocked by compliance: ${blocked}` });

  // ── Atomic transfer ───────────────────────────────────────────────────────
  let paymentId;
  try {
    await query('BEGIN');
    await query('UPDATE users SET account_balance = account_balance - $1 WHERE id = $2', [amt, req.userId]);
    await query('UPDATE users SET account_balance = account_balance + $1 WHERE id = $2', [amt, toUserId]);
    const payRes = await query(
      `INSERT INTO payments (from_user, to_user, amount, method, note) VALUES ($1, $2, $3, 'in_app', $4) RETURNING id`,
      [req.userId, toUserId, amt, note || null]
    );
    paymentId = payRes.rows[0].id;
    await query('COMMIT');
  } catch (err) {
    await query('ROLLBACK');
    console.error('[DIRECT PAY] failed:', err.message);
    return res.status(500).json({ message: 'Payment failed. Please try again.' });
  }

  // ── Post-payment AML monitoring (async, non-blocking — same as spaces) ────
  checkPayment(req.userId, amt, paymentId).catch(() => {});

  notifyUser(toUserId, 'Payment received',
    `${sender.name} sent you €${amt.toFixed(2)}${note ? ': ' + note : ''}`,
    { type: 'payment_received', fromUserId: req.userId, amount: amt }
  ).catch(() => {});

  const newBal = (await query('SELECT account_balance FROM users WHERE id=$1', [req.userId])).rows[0].account_balance;
  res.json({ message: `Payment of €${amt.toFixed(2)} sent to ${recipient.name}`, newBalance: parseFloat(newBal) });
});

module.exports = router;
