/**
 * FCM HTTP v1 API
 * Legacy fcm/send (server key) was shut down by Google in June 2024.
 * This implementation uses a service account JWT → OAuth2 access token → v1 API.
 *
 * Setup: add to .env
 *   FCM_PROJECT_ID=splitpay-a6e4a
 *   GOOGLE_SERVICE_ACCOUNT={"type":"service_account","project_id":...}   ← paste the full JSON
 *   OR
 *   GOOGLE_SERVICE_ACCOUNT_PATH=./service-account.json
 */

'use strict';

const https  = require('https');
const crypto = require('crypto');
const fs     = require('fs');
const { query } = require('../config/db');

const FCM_PROJECT_ID = process.env.FCM_PROJECT_ID || 'splitpay-a6e4a';

// ── Load service account ──────────────────────────────────────────────────────
let serviceAccount = null;
try {
  if (process.env.GOOGLE_SERVICE_ACCOUNT) {
    serviceAccount = JSON.parse(process.env.GOOGLE_SERVICE_ACCOUNT);
  } else if (process.env.GOOGLE_SERVICE_ACCOUNT_PATH) {
    serviceAccount = JSON.parse(fs.readFileSync(process.env.GOOGLE_SERVICE_ACCOUNT_PATH, 'utf8'));
  }
  if (serviceAccount) console.log('[FCM] Service account loaded for:', serviceAccount.client_email);
  else console.warn('[FCM] No service account configured — notifications will not be sent');
} catch (e) {
  console.warn('[FCM] Failed to load service account:', e.message);
}

// ── OAuth2 access token (cached, ~1h lifetime) ────────────────────────────────
let _cachedToken  = null;
let _tokenExpires = 0;

async function getAccessToken() {
  if (_cachedToken && Date.now() < _tokenExpires) return _cachedToken;
  if (!serviceAccount) return null;

  const now  = Math.floor(Date.now() / 1000);
  const claim = {
    iss:   serviceAccount.client_email,
    scope: 'https://www.googleapis.com/auth/firebase.messaging',
    aud:   'https://oauth2.googleapis.com/token',
    iat:   now,
    exp:   now + 3600,
  };

  // Build signed JWT (RS256) using the service account private key
  const header  = Buffer.from(JSON.stringify({ alg: 'RS256', typ: 'JWT' })).toString('base64url');
  const payload = Buffer.from(JSON.stringify(claim)).toString('base64url');
  const sign    = crypto.createSign('RSA-SHA256');
  sign.update(`${header}.${payload}`);
  const sig = sign.sign(serviceAccount.private_key, 'base64url');
  const jwt = `${header}.${payload}.${sig}`;

  // Exchange JWT for an access token
  const body = `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${jwt}`;
  const token = await new Promise((resolve) => {
    const req = https.request(
      {
        hostname: 'oauth2.googleapis.com',
        path:     '/token',
        method:   'POST',
        headers:  { 'Content-Type': 'application/x-www-form-urlencoded', 'Content-Length': Buffer.byteLength(body) },
      },
      (res) => {
        let data = '';
        res.on('data', c => data += c);
        res.on('end', () => {
          try { resolve(JSON.parse(data).access_token || null); }
          catch { resolve(null); }
        });
      }
    );
    req.on('error', () => resolve(null));
    req.write(body);
    req.end();
  });

  if (token) {
    _cachedToken  = token;
    _tokenExpires = Date.now() + 3500_000; // 58 minutes
  } else {
    console.error('[FCM] Failed to obtain access token');
  }
  return token;
}

// ── Send a single push notification ──────────────────────────────────────────
async function sendFcm(fcmToken, title, body, data = {}) {
  const accessToken = await getAccessToken();
  if (!accessToken) return;

  const payload = JSON.stringify({
    message: {
      token: fcmToken,
      notification: { title, body },
      data: Object.fromEntries(Object.entries(data).map(([k, v]) => [k, String(v)])),
    },
  });

  return new Promise((resolve) => {
    const req = https.request(
      {
        hostname: 'fcm.googleapis.com',
        path:     `/v1/projects/${FCM_PROJECT_ID}/messages:send`,
        method:   'POST',
        headers:  {
          'Authorization':  `Bearer ${accessToken}`,
          'Content-Type':   'application/json',
          'Content-Length': Buffer.byteLength(payload),
        },
      },
      (res) => {
        let resp = '';
        res.on('data', c => resp += c);
        res.on('end', () => {
          if (res.statusCode !== 200) {
            console.error(`[FCM] Send failed (${res.statusCode}):`, resp.slice(0, 300));
            // If token is stale (404), delete it from DB
            if (res.statusCode === 404) {
              query('DELETE FROM fcm_tokens WHERE token=$1', [fcmToken]).catch(() => {});
            }
            // If OAuth token expired (401), clear cache so it refreshes
            if (res.statusCode === 401) {
              _cachedToken = null;
            }
          }
          resolve(res.statusCode);
        });
      }
    );
    req.on('error', (e) => { console.error('[FCM] Network error:', e.message); resolve(null); });
    req.setTimeout(5000, () => { req.destroy(); resolve(null); });
    req.write(payload);
    req.end();
  });
}

// ── Token management ──────────────────────────────────────────────────────────
async function upsertToken(userId, token) {
  await query('DELETE FROM fcm_tokens WHERE token=$1', [token]);
  await query('INSERT INTO fcm_tokens (user_id, token, updated_at) VALUES ($1,$2,NOW())', [userId, token]);
}

async function tokensForUsers(userIds) {
  if (!userIds.length) return [];
  const result = await query('SELECT token FROM fcm_tokens WHERE user_id = ANY($1)', [userIds]);
  return result.rows.map(r => r.token);
}

// ── Public notification helpers ───────────────────────────────────────────────
async function notifyUser(userId, title, body, data = {}) {
  const tokens = await tokensForUsers([userId]);
  for (const token of tokens) await sendFcm(token, title, body, data);
}

async function notifyAdmins(title, body, data = {}) {
  const admins = await query('SELECT id FROM users WHERE is_admin=true AND is_deleted=false');
  const tokens = await tokensForUsers(admins.rows.map(r => r.id));
  for (const token of tokens) await sendFcm(token, title, body, data);
}

async function notifyGroupMembers(groupId, excludeUserId, memberIds, title, body) {
  const targets = excludeUserId ? memberIds.filter(id => id !== excludeUserId) : memberIds;
  const tokens  = await tokensForUsers(targets);
  for (const token of tokens) await sendFcm(token, title, body, { groupId });
}

module.exports = { upsertToken, notifyUser, notifyAdmins, notifyGroupMembers };
