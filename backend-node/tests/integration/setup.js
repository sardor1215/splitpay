/**
 * Integration test helpers.
 * Requires a running server at BASE_URL and a live DB.
 */

'use strict';

require('dotenv').config({ path: require('path').resolve(__dirname, '../../.env') });
const { pool } = require('../../src/config/db');

const BASE_URL = process.env.TEST_BASE_URL || 'http://localhost:3000';

// ── HTTP helpers ──────────────────────────────────────────────────────────────

async function api(method, path, body, token) {
  const opts = {
    method,
    headers: { 'Content-Type': 'application/json' },
  };
  if (token) opts.headers['Authorization'] = `Bearer ${token}`;
  if (body)  opts.body = JSON.stringify(body);

  const res = await fetch(`${BASE_URL}${path}`, opts);
  const data = await res.json().catch(() => ({}));
  return { status: res.status, data };
}

const get  = (path, token)        => api('GET',    path, null, token);
const post = (path, body, token)  => api('POST',   path, body, token);
const patch = (path, body, token) => api('PATCH',  path, body, token);
const del  = (path, token)        => api('DELETE', path, null, token);

// ── Server health check ───────────────────────────────────────────────────────

async function isServerUp() {
  try {
    const res = await fetch(`${BASE_URL}/health`, { signal: AbortSignal.timeout(3000) });
    return res.ok;
  } catch {
    return false;
  }
}

// ── Test-user factory ─────────────────────────────────────────────────────────

let userCounter = 0;

async function createTestUser(opts = {}) {
  const n        = ++userCounter;
  const ts       = Date.now();
  const email    = opts.email    || `test_${ts}_${n}@splitpay.test`;
  const name     = opts.name     || `TestUser${n}`;
  const password = opts.password || 'Test@1234!';

  // Register (returns only { message } — no tokens)
  const { status: regStatus, data: regData } = await post('/auth/register', { name, email, password });
  if (regStatus !== 201) throw new Error(`Register failed (${regStatus}): ${JSON.stringify(regData)}`);

  // Get the newly-created user ID from DB (register doesn't return it)
  const result = await pool.query('SELECT id FROM users WHERE email=$1', [email]);
  const userId = result.rows[0]?.id;
  if (!userId) throw new Error('Register returned no user.id');

  // Force kyc_status=approved so AML R6 doesn't block expenses > $150
  await pool.query(
    `UPDATE users SET kyc_status='approved', aml_status='clear' WHERE id=$1`,
    [userId],
  );

  // Login to get access + refresh tokens (register marks is_verified=true)
  const { status: loginStatus, data: loginData } = await post('/auth/login', { email, password });
  if (loginStatus !== 200) throw new Error(`Login failed (${loginStatus}): ${JSON.stringify(loginData)}`);

  return {
    id:           loginData.userId,
    name:         loginData.name,
    email:        loginData.email,
    token:        loginData.accessToken,
    accessToken:  loginData.accessToken,
    refreshToken: loginData.refreshToken,
    password,
  };
}

// ── Group factory ─────────────────────────────────────────────────────────────

async function createTestGroup(token, opts = {}) {
  const { status, data } = await post('/groups', {
    name:  opts.name  || 'Test Group',
    emoji: opts.emoji || '🧪',
  }, token);
  if (status !== 201) throw new Error(`Create group failed (${status}): ${JSON.stringify(data)}`);
  return data;
}

// ── Cleanup ───────────────────────────────────────────────────────────────────

async function cleanup(emails) {
  if (!emails?.length) return;
  await pool.query(
    `UPDATE users SET is_deleted=true WHERE email = ANY($1)`,
    [emails],
  );
}

module.exports = { api, get, post, patch, del, isServerUp, createTestUser, createTestGroup, cleanup, pool };
