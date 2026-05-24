/**
 * Integration tests — Expense (Space) management & split-mode correctness
 *
 * All expense logic is now handled by the Spaces API (/groups/:id/spaces).
 * These tests verify:
 *   - Space creation with equally / exact / percentage split modes
 *   - Share computation correctness (including odd participant counts)
 *   - Validation errors (missing fields, bad amount, no participants)
 *   - List retrieval
 *   - Edit (PATCH) and delete (DELETE)
 *   - AML block for unverified users (R6 — no KYC + amount > SDD threshold)
 */

'use strict';

const { describe, test, before, after } = require('node:test');
const assert = require('node:assert/strict');
// setup must be first — it loads dotenv before db.js initialises the pool
const {
  isServerUp, post, get, patch, del,
  createTestUser, createTestGroup, cleanup, pool,
} = require('./setup');

const createdEmails = [];
let alice, bob, carol, group, groupId;

// Helper: create a space in the test group
async function createSpace(token, overrides = {}) {
  return post(`/groups/${groupId}/spaces`, {
    name:           overrides.name           ?? 'Test Expense',
    totalAmount:    overrides.totalAmount     ?? 100,
    splitMode:      overrides.splitMode       ?? 'equally',
    settlementMode: overrides.settlementMode  ?? 'PAY',
    participants:   overrides.participants    ?? [{ userId: alice.id }, { userId: bob.id }],
    ...overrides,
  }, token);
}

describe('Expenses integration', async () => {
  before(async () => {
    if (!await isServerUp()) {
      console.warn('[SKIP] Server not running — skipping expense integration tests');
      process.exit(0);
    }

    alice = await createTestUser({ name: 'Alice' });
    bob   = await createTestUser({ name: 'Bob'   });
    carol = await createTestUser({ name: 'Carol'  });
    createdEmails.push(alice.email, bob.email, carol.email);

    group   = await createTestGroup(alice.token, { name: 'Expense Test Group' });
    groupId = group.id;

    // Add Bob and Carol directly (bypass invitation for setup speed)
    const { v4: uuidv4 } = require('uuid');
    await pool.query(
      'INSERT INTO group_members (id,group_id,user_id,role,joined_at) VALUES ($1,$2,$3,$4,NOW())',
      [uuidv4(), groupId, bob.id, 'member'],
    );
    await pool.query(
      'INSERT INTO group_members (id,group_id,user_id,role,joined_at) VALUES ($1,$2,$3,$4,NOW())',
      [uuidv4(), groupId, carol.id, 'member'],
    );
  });

  after(async () => cleanup(createdEmails));

  // ── Equally split ───────────────────────────────────────────────────────────

  describe('POST /groups/:id/spaces — equally split', () => {
    test('2 participants (€100) → €50 each', async () => {
      const { status, data } = await createSpace(alice.token, {
        name: 'Dinner for 2', totalAmount: 100, splitMode: 'equally',
        participants: [{ userId: alice.id }, { userId: bob.id }],
      });

      assert.equal(status, 201);
      assert.equal(data.totalAmount, 100);
      assert.equal(data.splitMode, 'equally');
      assert.equal(data.participants.length, 2);
      data.participants.forEach(p => assert.equal(p.share, 50));
    });

    test('3 participants (€90) → €30 each (odd count)', async () => {
      const { status, data } = await createSpace(bob.token, {
        name: 'Pizza for 3', totalAmount: 90, splitMode: 'equally',
        participants: [{ userId: alice.id }, { userId: bob.id }, { userId: carol.id }],
      });

      assert.equal(status, 201);
      data.participants.forEach(p => assert.equal(p.share, 30));
    });

    test('1 participant (self-expense) → full amount', async () => {
      const { status, data } = await createSpace(alice.token, {
        name: 'Solo coffee', totalAmount: 5, splitMode: 'equally',
        participants: [{ userId: alice.id }],
      });

      assert.equal(status, 201);
      assert.equal(data.participants[0].share, 5);
    });
  });

  // ── Exact split ─────────────────────────────────────────────────────────────

  describe('POST /groups/:id/spaces — exact split', () => {
    test('exact shares are respected', async () => {
      const { status, data } = await createSpace(alice.token, {
        name: 'Exact split', totalAmount: 90, splitMode: 'exact',
        participants: [
          { userId: alice.id, share: 40 },
          { userId: bob.id,   share: 50 },
        ],
      });

      assert.equal(status, 201);
      const aliceShare = data.participants.find(p => p.userId === alice.id)?.share;
      const bobShare   = data.participants.find(p => p.userId === bob.id)?.share;
      assert.equal(aliceShare, 40);
      assert.equal(bobShare,   50);
    });
  });

  // ── Percentage split ────────────────────────────────────────────────────────

  describe('POST /groups/:id/spaces — percentage split', () => {
    test('70% / 30% of €200 → €140 / €60', async () => {
      const { status, data } = await createSpace(bob.token, {
        name: 'Percentage split', totalAmount: 200, splitMode: 'percentage',
        participants: [
          { userId: bob.id,   share: 70 },
          { userId: carol.id, share: 30 },
        ],
      });

      assert.equal(status, 201);
      const bobShare   = data.participants.find(p => p.userId === bob.id)?.share;
      const carolShare = data.participants.find(p => p.userId === carol.id)?.share;
      assert.equal(bobShare,   140);
      assert.equal(carolShare,  60);
    });

    test('50% / 50% of €100 → €50 / €50', async () => {
      const { status, data } = await createSpace(alice.token, {
        name: 'Half-half', totalAmount: 100, splitMode: 'percentage',
        participants: [
          { userId: alice.id, share: 50 },
          { userId: bob.id,   share: 50 },
        ],
      });

      assert.equal(status, 201);
      data.participants.forEach(p => assert.equal(p.share, 50));
    });
  });

  // ── Validation ──────────────────────────────────────────────────────────────

  describe('Space creation validation', () => {
    test('missing name → 400', async () => {
      const { status } = await post(`/groups/${groupId}/spaces`, {
        totalAmount: 50, splitMode: 'equally',
        participants: [{ userId: alice.id }],
      }, alice.token);
      assert.equal(status, 400);
    });

    test('amount ≤ 0 → 400', async () => {
      const { status } = await post(`/groups/${groupId}/spaces`, {
        name: 'Bad', totalAmount: -10, splitMode: 'equally',
        participants: [{ userId: alice.id }],
      }, alice.token);
      assert.equal(status, 400);
    });

    test('no participants → 400', async () => {
      const { status } = await post(`/groups/${groupId}/spaces`, {
        name: 'No pts', totalAmount: 50, splitMode: 'equally', participants: [],
      }, alice.token);
      assert.equal(status, 400);
    });

    test('PLAN mode without due date → 400', async () => {
      const { status } = await post(`/groups/${groupId}/spaces`, {
        name: 'No date', totalAmount: 50, splitMode: 'equally',
        settlementMode: 'PLAN',
        participants: [{ userId: alice.id }],
      }, alice.token);
      assert.equal(status, 400);
    });

    test('no auth token → 401', async () => {
      const { status } = await post(`/groups/${groupId}/spaces`, {
        name: 'Unauth', totalAmount: 50, splitMode: 'equally',
        participants: [{ userId: alice.id }],
      });
      assert.equal(status, 401);
    });
  });

  // ── Space creation response fields ─────────────────────────────────────────

  describe('Space response fields', () => {
    test('created space has all required fields', async () => {
      const { status, data } = await createSpace(alice.token, {
        name: 'Field check', totalAmount: 60, splitMode: 'equally',
        participants: [{ userId: alice.id }, { userId: bob.id }, { userId: carol.id }],
      });

      assert.equal(status, 201);
      assert.ok(data.id,                          'id required');
      assert.ok(data.name,                        'name required');
      assert.ok(typeof data.totalAmount === 'number', 'totalAmount must be a number');
      assert.ok(data.splitMode,                   'splitMode required');
      assert.ok(data.settlementMode,              'settlementMode required');
      assert.ok(data.status,                      'status required');
      assert.ok(Array.isArray(data.participants), 'participants must be an array');
      assert.equal(data.status, 'pending_acceptance', 'new space must be pending_acceptance');
    });

    test('participants have acceptanceStatus = "pending"', async () => {
      const { data } = await createSpace(alice.token, {
        name: 'Acceptance check', totalAmount: 80, splitMode: 'equally',
        participants: [{ userId: alice.id }, { userId: bob.id }],
      });

      data.participants.forEach(p => {
        assert.equal(p.acceptanceStatus, 'pending', `${p.userId} should be pending`);
      });
    });
  });

  // ── Space list ──────────────────────────────────────────────────────────────

  describe('GET /groups/:id/spaces', () => {
    test('returns array of spaces', async () => {
      const { status, data } = await get(`/groups/${groupId}/spaces`, alice.token);
      assert.equal(status, 200);
      assert.ok(Array.isArray(data));
      assert.ok(data.length >= 1, 'Should have at least one space');
    });

    test('each space has required fields', async () => {
      const { data } = await get(`/groups/${groupId}/spaces`, alice.token);
      const s = data[0];
      assert.ok(s.id);
      assert.ok(s.name);
      assert.ok(typeof s.totalAmount === 'number');
      assert.ok(s.splitMode);
      assert.ok(s.status);
    });
  });

  // ── Edit space ──────────────────────────────────────────────────────────────

  describe('PATCH /groups/:id/spaces/:spaceId', () => {
    test('creator can update name and category', async () => {
      const { data: created } = await createSpace(alice.token, {
        name: 'Old Name', totalAmount: 50,
        participants: [{ userId: alice.id }],
      });

      const { status, data } = await patch(
        `/groups/${groupId}/spaces/${created.id}`,
        { name: 'New Name', category: 'voyage' },
        alice.token,
      );

      assert.equal(status, 200);
      assert.equal(data.name, 'New Name');
      assert.equal(data.category, 'voyage');
    });

    test('non-creator cannot edit space → 403', async () => {
      const { data: created } = await createSpace(alice.token, {
        name: 'Alice owns this', totalAmount: 50,
        participants: [{ userId: alice.id }, { userId: bob.id }],
      });

      const { status } = await patch(
        `/groups/${groupId}/spaces/${created.id}`,
        { name: 'Bob trying to rename' },
        bob.token,
      );

      assert.equal(status, 403);
    });
  });

  // ── Delete space ────────────────────────────────────────────────────────────

  describe('DELETE /groups/:id/spaces/:spaceId', () => {
    test('creator can delete space → 200', async () => {
      const { data: created } = await createSpace(alice.token, {
        name: 'To delete', totalAmount: 20,
        participants: [{ userId: alice.id }],
      });

      const { status } = await del(
        `/groups/${groupId}/spaces/${created.id}`,
        alice.token,
      );
      assert.equal(status, 200);
    });

    test('non-creator cannot delete → 403', async () => {
      const { data: created } = await createSpace(alice.token, {
        name: 'Protected space', totalAmount: 30,
        participants: [{ userId: alice.id }, { userId: bob.id }],
      });

      const { status } = await del(
        `/groups/${groupId}/spaces/${created.id}`,
        bob.token,
      );
      assert.equal(status, 403);
    });

    test('delete non-existent → 404', async () => {
      const { status } = await del(
        `/groups/${groupId}/spaces/00000000-0000-0000-0000-000000000000`,
        alice.token,
      );
      assert.equal(status, 404);
    });
  });

  // ── AML blocking ────────────────────────────────────────────────────────────

  describe('AML block — R6 (no KYC + amount > SDD threshold)', () => {
    test('user without KYC cannot create space > €150 (R6 CDD block)', async () => {
      const ts    = Date.now();
      const email = `aml_nokyc_${ts}@splitpay.test`;
      createdEmails.push(email);

      // Register (is_verified stays false by default, or depends on setup)
      const regRes = await post('/auth/register', { name: 'NoKYC', email, password: 'Test@1234!' });
      const userId = regRes.data?.userId;

      const loginRes = await post('/auth/login', { email, password: 'Test@1234!' });
      assert.equal(loginRes.status, 200, 'Login should succeed');
      const { accessToken, userId: noKycId } = loginRes.data;

      // Add to group so the space creation can proceed to AML check
      const { v4: uuidv4 } = require('uuid');
      await pool.query(
        'INSERT INTO group_members (id,group_id,user_id,role,joined_at) VALUES ($1,$2,$3,$4,NOW()) ON CONFLICT DO NOTHING',
        [uuidv4(), groupId, noKycId, 'member'],
      );

      // €500 expense — kyc_status='none' → R6 BLOCK
      const { status } = await post(`/groups/${groupId}/spaces`, {
        name: 'Should be blocked by R6', totalAmount: 500,
        splitMode: 'equally', settlementMode: 'PAY',
        participants: [{ userId: noKycId }],
      }, accessToken);

      assert.equal(status, 422, 'AML R6 should block user without KYC for amounts > €150');
    });
  });
});
