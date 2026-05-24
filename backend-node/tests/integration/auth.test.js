/**
 * Integration tests — Authentication flows
 *
 * Scenarios:
 *   - Register new user
 *   - Login with correct / wrong credentials
 *   - Access protected route with valid / missing / expired token
 *   - Refresh token rotation
 *   - Logout invalidates refresh token
 *   - /me profile read and update
 */

'use strict';

const { describe, test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const { isServerUp, post, get, patch, createTestUser, cleanup } = require('./setup');

const createdEmails = [];

describe('Auth integration', async () => {
  before(async () => {
    if (!await isServerUp()) {
      console.warn('[SKIP] Server not running — skipping auth integration tests');
      process.exit(0);
    }
  });

  after(async () => cleanup(createdEmails));

  // ── Register ───────────────────────────────────────────────────────────────

  describe('POST /auth/register', () => {
    test('registers a new user → 201 + success message', async () => {
      const ts    = Date.now();
      const email = `register_test_${ts}@splitpay.test`;
      createdEmails.push(email);

      const { status, data } = await post('/auth/register', {
        name: 'Reg User', email, password: 'Secure@1234!',
      });

      assert.equal(status, 201);
      assert.ok(data.message, 'should return a success message');
    });

    test('register then login returns tokens', async () => {
      const ts    = Date.now();
      const email = `reg_login_${ts}@splitpay.test`;
      createdEmails.push(email);

      await post('/auth/register', { name: 'RL User', email, password: 'Secure@1234!' });
      const { status, data } = await post('/auth/login', { email, password: 'Secure@1234!' });

      assert.equal(status, 200);
      assert.ok(data.accessToken,  'login should return accessToken');
      assert.ok(data.refreshToken, 'login should return refreshToken');
      assert.ok(data.userId,       'login should return userId');
    });

    test('duplicate email → 409', async () => {
      const ts    = Date.now();
      const email = `dup_${ts}@splitpay.test`;
      createdEmails.push(email);

      await post('/auth/register', { name: 'A', email, password: 'Secure@1234!' });
      const { status } = await post('/auth/register', { name: 'B', email, password: 'Other@5678!' });

      assert.equal(status, 409);
    });

    test('missing name → 400', async () => {
      const { status } = await post('/auth/register', {
        email: `noname_${Date.now()}@splitpay.test`, password: 'Secure@1234!',
      });
      assert.equal(status, 400);
    });

    test('missing email → 400', async () => {
      const { status } = await post('/auth/register', {
        name: 'X', password: 'Secure@1234!',
      });
      assert.equal(status, 400);
    });
  });

  // ── Login ──────────────────────────────────────────────────────────────────

  describe('POST /auth/login', () => {
    test('correct credentials → 200 + tokens', async () => {
      const user = await createTestUser();
      createdEmails.push(user.email);

      // user was already logged in during createTestUser; test direct login again
      const { status, data } = await post('/auth/login', {
        email: user.email, password: user.password,
      });

      assert.equal(status, 200);
      assert.ok(data.accessToken,  'should have accessToken');
      assert.ok(data.refreshToken, 'should have refreshToken');
      assert.ok(data.userId,       'should have userId');
    });

    test('wrong password → 401', async () => {
      const user = await createTestUser();
      createdEmails.push(user.email);

      const { status } = await post('/auth/login', {
        email: user.email, password: 'WrongPassword!',
      });
      assert.equal(status, 401);
    });

    test('unknown email → 401', async () => {
      const { status } = await post('/auth/login', {
        email: 'nobody@nowhere.test', password: 'Anything123!',
      });
      assert.equal(status, 401);
    });
  });

  // ── Protected routes ───────────────────────────────────────────────────────

  describe('Protected route access', () => {
    test('valid token → 200', async () => {
      const user = await createTestUser();
      createdEmails.push(user.email);

      const { status, data } = await get('/me', user.token);
      assert.equal(status, 200);
      assert.equal(data.id, user.id);
    });

    test('no token → 401', async () => {
      const { status } = await get('/me');
      assert.equal(status, 401);
    });

    test('invalid / tampered token → 401', async () => {
      const { status } = await get('/me', 'Bearer eyJhbGciOiJIUzI1NiJ9.fake.token');
      assert.equal(status, 401);
    });
  });

  // ── Token refresh ──────────────────────────────────────────────────────────

  describe('POST /auth/refresh', () => {
    test('valid refresh token → new access token', async () => {
      const user = await createTestUser();
      createdEmails.push(user.email);

      const { status, data } = await post('/auth/refresh', { refreshToken: user.refreshToken });
      assert.equal(status, 200);
      assert.ok(data.accessToken, 'should return new accessToken');
    });

    test('missing refresh token → 401', async () => {
      // Server returns 401 for missing/null token (validateRefreshToken returns null)
      const { status } = await post('/auth/refresh', {});
      assert.equal(status, 401);
    });

    test('invalid refresh token → 401', async () => {
      const { status } = await post('/auth/refresh', { refreshToken: 'bad.token.here' });
      assert.equal(status, 401);
    });
  });

  // ── Logout ─────────────────────────────────────────────────────────────────

  describe('POST /auth/logout', () => {
    test('logout invalidates refresh token', async () => {
      const user = await createTestUser();
      createdEmails.push(user.email);

      const { accessToken, refreshToken } = user;

      // Logout
      const logout = await post('/auth/logout', { refreshToken }, accessToken);
      assert.equal(logout.status, 200);

      // Attempt to use the old refresh token → should fail
      const reuse = await post('/auth/refresh', { refreshToken });
      assert.equal(reuse.status, 401);
    });
  });

  // ── Profile ────────────────────────────────────────────────────────────────

  describe('GET /me and PATCH /me', () => {
    test('GET /me returns correct profile fields', async () => {
      const user = await createTestUser();
      createdEmails.push(user.email);

      const { status, data } = await get('/me', user.token);
      assert.equal(status, 200);
      assert.ok(data.id || data.userId, 'should return id or userId');
      assert.ok(data.email);
      assert.ok(data.name);
    });

    test('PATCH /me updates name', async () => {
      const user = await createTestUser();
      createdEmails.push(user.email);

      const newName = `Updated ${Date.now()}`;
      const { status, data } = await patch('/me', { name: newName }, user.token);
      assert.equal(status, 200);
      assert.equal(data.name, newName);
    });
  });
});
