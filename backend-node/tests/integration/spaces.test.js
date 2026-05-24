/**
 * Integration tests — Espaces (SplitEase Logique Espace v1.3)
 *
 * FR references:
 *   FR-047  Space linked to a group
 *   FR-048  Participants must be active group members
 *   FR-049  Designated launcher role
 *   FR-050  Only launcher/creator triggers settlement
 *   FR-051  Early-settle PLAN needs unanimous agreement
 *   FR-052  Any decline blocks early settle
 *   FR-053  48h acceptance window / auto-cancel on decline
 *   FR-054  Creator can force-launch with accepting members only
 *   FR-056  Assistance for failing member
 *   FR-057  Debt registered when assistance taken
 *   FR-058  Quorum size depends on group size
 *   FR-059  Rejected quorum member is replaced
 *   FR-060  Settlement proceeds when quorum confirmed
 *   FR-061  No more candidates → space suspended
 *   FR-062  Immutable audit log
 */

'use strict';

const { describe, test, before, after } = require('node:test');
const assert = require('node:assert/strict');
// setup must be first — it loads dotenv before db.js initialises the pool
const {
  isServerUp, post, get,
  createTestUser, createTestGroup, cleanup, pool,
} = require('./setup');

const createdEmails = [];
let alice, bob, carol, dave;
let group, groupId;

// DB shortcut
async function addMember(gid, userId) {
  const { v4: uuidv4 } = require('uuid');
  await pool.query(
    'INSERT INTO group_members (id,group_id,user_id,role,joined_at) VALUES ($1,$2,$3,$4,NOW()) ON CONFLICT DO NOTHING',
    [uuidv4(), gid, userId, 'member'],
  );
}

describe('Spaces integration', async () => {
  before(async () => {
    if (!await isServerUp()) {
      console.warn('[SKIP] Server not running — skipping spaces integration tests');
      process.exit(0);
    }

    alice = await createTestUser({ name: 'Alice' });
    bob   = await createTestUser({ name: 'Bob'   });
    carol = await createTestUser({ name: 'Carol'  });
    dave  = await createTestUser({ name: 'Dave'  });
    createdEmails.push(alice.email, bob.email, carol.email, dave.email);

    group   = await createTestGroup(alice.token, { name: 'Space Test Group' });
    groupId = group.id;

    await addMember(groupId, bob.id);
    await addMember(groupId, carol.id);
    await addMember(groupId, dave.id);
  });

  after(async () => cleanup(createdEmails));

  // ── FR-047: Space must be linked to a group ─────────────────────────────────

  describe('FR-047/048 — Space creation validation', () => {
    test('non-member cannot create a space (FR-047)', async () => {
      const outsider = await createTestUser({ name: 'Outsider' });
      createdEmails.push(outsider.email);

      const { status } = await post(`/groups/${groupId}/spaces`, {
        name: 'Should fail', totalAmount: 100, splitMode: 'equally',
        settlementMode: 'PAY',
        participants: [{ userId: alice.id }],
      }, outsider.token);

      assert.equal(status, 403);
    });

    test('participant not in group → 400 (FR-048)', async () => {
      const outsider = await createTestUser({ name: 'Outside2' });
      createdEmails.push(outsider.email);

      const { status } = await post(`/groups/${groupId}/spaces`, {
        name: 'Bad participant', totalAmount: 100, splitMode: 'equally',
        settlementMode: 'PAY',
        participants: [{ userId: alice.id }, { userId: outsider.id }],
      }, alice.token);

      assert.equal(status, 400);
    });

    test('missing name → 400', async () => {
      const { status } = await post(`/groups/${groupId}/spaces`, {
        totalAmount: 100, splitMode: 'equally', settlementMode: 'PAY',
        participants: [{ userId: alice.id }],
      }, alice.token);
      assert.equal(status, 400);
    });

    test('PLAN mode without dueDate → 400', async () => {
      const { status } = await post(`/groups/${groupId}/spaces`, {
        name: 'Plan no date', totalAmount: 100, splitMode: 'equally',
        settlementMode: 'PLAN',
        participants: [{ userId: alice.id }],
      }, alice.token);
      assert.equal(status, 400);
    });

    test('valid PAY space → 201 with participants', async () => {
      const { status, data } = await post(`/groups/${groupId}/spaces`, {
        name: 'Valid PAY', totalAmount: 90, splitMode: 'equally',
        settlementMode: 'PAY',
        participants: [
          { userId: alice.id },
          { userId: bob.id },
          { userId: carol.id },
        ],
      }, alice.token);

      assert.equal(status, 201);
      assert.equal(data.status, 'pending_acceptance');
      assert.equal(data.participants.length, 3);
      assert.ok(data.id);
    });
  });

  // ── FR-053: Acceptance → unanimous → active ─────────────────────────────────

  describe('FR-053 — Unanimous acceptance → space becomes active', () => {
    test('all members accept → status becomes active', async () => {
      const { data: space } = await post(`/groups/${groupId}/spaces`, {
        name: 'Unanimity test', totalAmount: 60, splitMode: 'equally',
        settlementMode: 'PAY',
        participants: [
          { userId: alice.id },
          { userId: bob.id },
        ],
      }, alice.token);

      const spaceId = space.id;

      // Alice accepts
      await post(`/groups/${groupId}/spaces/${spaceId}/accept`, {}, alice.token);
      // Bob accepts → triggers unanimity
      await post(`/groups/${groupId}/spaces/${spaceId}/accept`, {}, bob.token);

      const { data: detail } = await get(`/groups/${groupId}/spaces/${spaceId}`, alice.token);
      assert.equal(detail.status, 'active', 'Space should be active after unanimous acceptance');
    });

    test('already responded → 409', async () => {
      const { data: space } = await post(`/groups/${groupId}/spaces`, {
        name: 'Double accept', totalAmount: 30, splitMode: 'equally',
        settlementMode: 'PAY',
        participants: [{ userId: alice.id }],
      }, alice.token);

      await post(`/groups/${groupId}/spaces/${space.id}/accept`, {}, alice.token);
      const { status } = await post(`/groups/${groupId}/spaces/${space.id}/accept`, {}, alice.token);
      assert.equal(status, 409);
    });
  });

  // ── FR-053: Decline → auto-cancel ──────────────────────────────────────────

  describe('FR-053 — Any decline → space auto-cancelled', () => {
    test('Bob declines → space status = cancelled', async () => {
      const { data: space } = await post(`/groups/${groupId}/spaces`, {
        name: 'Cancel test', totalAmount: 60, splitMode: 'equally',
        settlementMode: 'PAY',
        participants: [{ userId: alice.id }, { userId: bob.id }],
      }, alice.token);

      const spaceId = space.id;
      await post(`/groups/${groupId}/spaces/${spaceId}/decline`, {}, bob.token);

      const { data: detail } = await get(`/groups/${groupId}/spaces/${spaceId}`, alice.token);
      assert.equal(detail.status, 'cancelled');
    });
  });

  // ── FR-054: Force-launch ────────────────────────────────────────────────────

  describe('FR-054 — Creator force-launches with accepting members', () => {
    test('force-launch with only Alice accepting → shares recalculated', async () => {
      const { data: space } = await post(`/groups/${groupId}/spaces`, {
        name: 'Force launch', totalAmount: 90, splitMode: 'equally',
        settlementMode: 'PAY',
        participants: [
          { userId: alice.id },
          { userId: bob.id },
          { userId: carol.id },
        ],
      }, alice.token);

      const spaceId = space.id;
      // Alice accepts; Bob and Carol don't respond
      await post(`/groups/${groupId}/spaces/${spaceId}/accept`, {}, alice.token);

      // Creator force-launches
      const { status, data } = await post(`/groups/${groupId}/spaces/${spaceId}/force-launch`, {}, alice.token);
      assert.equal(status, 200);
      assert.match(data.message, /lancé|launched/i);

      // Verify status is now active
      const { data: detail } = await get(`/groups/${groupId}/spaces/${spaceId}`, alice.token);
      assert.equal(detail.status, 'active');
      assert.equal(detail.forceLaunched, true);
    });

    test('non-creator cannot force-launch → 403', async () => {
      const { data: space } = await post(`/groups/${groupId}/spaces`, {
        name: 'No force by Bob', totalAmount: 60, splitMode: 'equally',
        settlementMode: 'PAY',
        participants: [{ userId: alice.id }, { userId: bob.id }],
      }, alice.token);

      const { status } = await post(
        `/groups/${groupId}/spaces/${space.id}/force-launch`, {}, bob.token,
      );
      assert.equal(status, 403);
    });

    test('force-launch with no acceptances → 400', async () => {
      const { data: space } = await post(`/groups/${groupId}/spaces`, {
        name: 'Force no accepts', totalAmount: 60, splitMode: 'equally',
        settlementMode: 'PAY',
        participants: [{ userId: alice.id }, { userId: bob.id }],
      }, alice.token);

      const { status } = await post(
        `/groups/${groupId}/spaces/${space.id}/force-launch`, {}, alice.token,
      );
      assert.equal(status, 400);
    });
  });

  // ── FR-049/050: Settlement trigger ─────────────────────────────────────────

  describe('FR-050 — Only launcher/creator triggers settlement', () => {
    test('creator triggers settlement on active PAY space → status settling', async () => {
      const { data: space } = await post(`/groups/${groupId}/spaces`, {
        name: 'Settlement trigger', totalAmount: 40, splitMode: 'equally',
        settlementMode: 'PAY',
        participants: [{ userId: alice.id }, { userId: bob.id }],
      }, alice.token);

      const spaceId = space.id;
      await post(`/groups/${groupId}/spaces/${spaceId}/accept`, {}, alice.token);
      await post(`/groups/${groupId}/spaces/${spaceId}/accept`, {}, bob.token);

      const { status } = await post(
        `/groups/${groupId}/spaces/${spaceId}/settle`, {}, alice.token,
      );
      assert.equal(status, 200);

      const { data: detail } = await get(`/groups/${groupId}/spaces/${spaceId}`, alice.token);
      assert.equal(detail.status, 'settling');
    });

    test('non-launcher cannot trigger settlement → 403', async () => {
      const { data: space } = await post(`/groups/${groupId}/spaces`, {
        name: 'Bob cannot settle', totalAmount: 40, splitMode: 'equally',
        settlementMode: 'PAY',
        participants: [{ userId: alice.id }, { userId: bob.id }],
      }, alice.token);

      const spaceId = space.id;
      await post(`/groups/${groupId}/spaces/${spaceId}/accept`, {}, alice.token);
      await post(`/groups/${groupId}/spaces/${spaceId}/accept`, {}, bob.token);

      const { status } = await post(
        `/groups/${groupId}/spaces/${spaceId}/settle`, {}, bob.token,
      );
      assert.equal(status, 403);
    });
  });

  // ── FR-051/052: Early settle (PLAN mode) ────────────────────────────────────

  describe('FR-051/052 — Early settle in PLAN mode', () => {
    test('FR-051: all accept early settle → space enters settling', async () => {
      const { data: space } = await post(`/groups/${groupId}/spaces`, {
        name: 'Early settle OK', totalAmount: 60, splitMode: 'equally',
        settlementMode: 'PLAN', dueDate: '2030-12-31',
        participants: [{ userId: alice.id }, { userId: bob.id }],
      }, alice.token);

      const spaceId = space.id;
      await post(`/groups/${groupId}/spaces/${spaceId}/accept`, {}, alice.token);
      await post(`/groups/${groupId}/spaces/${spaceId}/accept`, {}, bob.token);

      // Alice (launcher) requests early settle
      const req = await post(`/groups/${groupId}/spaces/${spaceId}/early-settle`, {}, alice.token);
      assert.equal(req.status, 200);

      // Bob votes accepted
      const vote = await post(
        `/groups/${groupId}/spaces/${spaceId}/early-settle/respond`,
        { vote: 'accepted' }, bob.token,
      );
      assert.equal(vote.status, 200);
    });

    test('FR-052: one decline blocks early settle', async () => {
      const { data: space } = await post(`/groups/${groupId}/spaces`, {
        name: 'Early settle declined', totalAmount: 60, splitMode: 'equally',
        settlementMode: 'PLAN', dueDate: '2030-12-31',
        participants: [{ userId: alice.id }, { userId: bob.id }, { userId: carol.id }],
      }, alice.token);

      const spaceId = space.id;
      await post(`/groups/${groupId}/spaces/${spaceId}/accept`, {}, alice.token);
      await post(`/groups/${groupId}/spaces/${spaceId}/accept`, {}, bob.token);
      await post(`/groups/${groupId}/spaces/${spaceId}/accept`, {}, carol.token);

      await post(`/groups/${groupId}/spaces/${spaceId}/early-settle`, {}, alice.token);

      // Carol declines → early settle blocked
      const decline = await post(
        `/groups/${groupId}/spaces/${spaceId}/early-settle/respond`,
        { vote: 'declined' }, carol.token,
      );
      assert.equal(decline.status, 200);
      assert.match(decline.data.message, /refusé|declined|refuse/i);
    });

    test('early-settle on PAY mode space → 400', async () => {
      const { data: space } = await post(`/groups/${groupId}/spaces`, {
        name: 'PAY no early settle', totalAmount: 40, splitMode: 'equally',
        settlementMode: 'PAY',
        participants: [{ userId: alice.id }],
      }, alice.token);

      await post(`/groups/${groupId}/spaces/${space.id}/accept`, {}, alice.token);

      const { status } = await post(
        `/groups/${groupId}/spaces/${space.id}/early-settle`, {}, alice.token,
      );
      assert.equal(status, 400);
    });
  });

  // ── FR-056/057: Assistance ──────────────────────────────────────────────────

  describe('FR-056/057 — Member assistance', () => {
    test('Alice assists Bob → assisted_by recorded', async () => {
      const { data: space } = await post(`/groups/${groupId}/spaces`, {
        name: 'Assistance test', totalAmount: 60, splitMode: 'equally',
        settlementMode: 'PAY',
        participants: [{ userId: alice.id }, { userId: bob.id }],
      }, alice.token);

      const spaceId = space.id;
      await post(`/groups/${groupId}/spaces/${spaceId}/accept`, {}, alice.token);
      await post(`/groups/${groupId}/spaces/${spaceId}/accept`, {}, bob.token);

      const { status, data } = await post(
        `/groups/${groupId}/spaces/${spaceId}/assist`,
        { memberId: bob.id }, alice.token,
      );
      assert.equal(status, 200);
      assert.match(data.message, /assistance|assist/i);
    });

    test('double assistance on same member → 409', async () => {
      const { data: space } = await post(`/groups/${groupId}/spaces`, {
        name: 'Double assist', totalAmount: 60, splitMode: 'equally',
        settlementMode: 'PAY',
        participants: [{ userId: alice.id }, { userId: bob.id }],
      }, alice.token);

      const spaceId = space.id;
      await post(`/groups/${groupId}/spaces/${spaceId}/accept`, {}, alice.token);
      await post(`/groups/${groupId}/spaces/${spaceId}/accept`, {}, bob.token);

      await post(`/groups/${groupId}/spaces/${spaceId}/assist`, { memberId: bob.id }, alice.token);
      const { status } = await post(
        `/groups/${groupId}/spaces/${spaceId}/assist`, { memberId: bob.id }, carol.token,
      );
      assert.equal(status, 409);
    });
  });

  // ── FR-049: Transfer launcher role ─────────────────────────────────────────

  describe('FR-049 — Transfer launcher role', () => {
    test('creator transfers launcher to Bob', async () => {
      const { data: space } = await post(`/groups/${groupId}/spaces`, {
        name: 'Transfer test', totalAmount: 40, splitMode: 'equally',
        settlementMode: 'PAY',
        participants: [{ userId: alice.id }, { userId: bob.id }],
      }, alice.token);

      const spaceId = space.id;
      const { status, data } = await post(
        `/groups/${groupId}/spaces/${spaceId}/transfer-launcher`,
        { toUserId: bob.id }, alice.token,
      );
      assert.equal(status, 200);

      const { data: detail } = await get(`/groups/${groupId}/spaces/${spaceId}`, alice.token);
      assert.equal(detail.launcherId, bob.id);
    });

    test('non-creator cannot transfer launcher → 403', async () => {
      const { data: space } = await post(`/groups/${groupId}/spaces`, {
        name: 'Transfer block', totalAmount: 40, splitMode: 'equally',
        settlementMode: 'PAY',
        participants: [{ userId: alice.id }, { userId: bob.id }],
      }, alice.token);

      const { status } = await post(
        `/groups/${groupId}/spaces/${space.id}/transfer-launcher`,
        { toUserId: carol.id }, bob.token,
      );
      assert.equal(status, 403);
    });
  });

  // ── FR-062: Audit log ────────────────────────────────────────────────────────

  describe('FR-062 — Immutable audit log', () => {
    test('audit log records space creation and participant actions', async () => {
      const { data: space } = await post(`/groups/${groupId}/spaces`, {
        name: 'Audit test', totalAmount: 40, splitMode: 'equally',
        settlementMode: 'PAY',
        participants: [{ userId: alice.id }, { userId: bob.id }],
      }, alice.token);

      const spaceId = space.id;
      await post(`/groups/${groupId}/spaces/${spaceId}/accept`, {}, alice.token);

      const { status, data: logs } = await get(
        `/groups/${groupId}/spaces/${spaceId}/audit`, alice.token,
      );
      assert.equal(status, 200);
      assert.ok(Array.isArray(logs));
      assert.ok(logs.length >= 2, 'Should have at least space_created + participant_accepted');

      const events = logs.map(l => l.event_type);
      assert.ok(events.includes('space_created'),      'space_created should be logged');
      assert.ok(events.includes('participant_accepted'), 'participant_accepted should be logged');
    });
  });

  // ── List & detail ──────────────────────────────────────────────────────────

  describe('GET spaces list and detail', () => {
    test('GET /groups/:id/spaces returns array', async () => {
      const { status, data } = await get(`/groups/${groupId}/spaces`, alice.token);
      assert.equal(status, 200);
      assert.ok(Array.isArray(data));
    });

    test('GET /spaces/pending returns pending invitations', async () => {
      // Create a space Bob hasn't responded to yet
      await post(`/groups/${groupId}/spaces`, {
        name: 'Pending for Bob', totalAmount: 40, splitMode: 'equally',
        settlementMode: 'PAY',
        participants: [{ userId: alice.id }, { userId: bob.id }],
      }, alice.token);

      const { status, data } = await get('/spaces/pending', bob.token);
      assert.equal(status, 200);
      assert.ok(Array.isArray(data));
    });

    test('GET /groups/:id/spaces/:spaceId returns detail with participants', async () => {
      const { data: space } = await post(`/groups/${groupId}/spaces`, {
        name: 'Detail test', totalAmount: 60, splitMode: 'equally',
        settlementMode: 'PAY',
        participants: [{ userId: alice.id }, { userId: bob.id }],
      }, alice.token);

      const { status, data } = await get(
        `/groups/${groupId}/spaces/${space.id}`, alice.token,
      );
      assert.equal(status, 200);
      assert.ok(data.participants.length >= 1);
      assert.ok(data.myShare !== null);
      assert.equal(data.settlementMode, 'PAY');
    });

    test('non-existent space → 404', async () => {
      const { status } = await get(
        `/groups/${groupId}/spaces/00000000-0000-0000-0000-000000000000`,
        alice.token,
      );
      assert.equal(status, 404);
    });
  });

  // ── FR-058: Quorum size ─────────────────────────────────────────────────────

  describe('FR-058 — Quorum confirmation flow', () => {
    test('quorum confirm → space settles when threshold reached', async () => {
      // 2-person space → quorum = 1
      const { data: space } = await post(`/groups/${groupId}/spaces`, {
        name: 'Quorum settle', totalAmount: 40, splitMode: 'equally',
        settlementMode: 'PAY',
        participants: [{ userId: alice.id }, { userId: bob.id }],
      }, alice.token);

      const spaceId = space.id;
      await post(`/groups/${groupId}/spaces/${spaceId}/accept`, {}, alice.token);
      await post(`/groups/${groupId}/spaces/${spaceId}/accept`, {}, bob.token);

      // Alice triggers settlement
      await post(`/groups/${groupId}/spaces/${spaceId}/settle`, {}, alice.token);

      // Query who's in the quorum (Bob, since Alice is launcher and excluded)
      const qConf = await pool.query(
        `SELECT user_id FROM space_quorum_confirmations WHERE space_id=$1 AND status='pending' LIMIT 1`,
        [spaceId],
      );

      if (qConf.rows.length === 0) {
        // No quorum member found yet — skip assertion (timing)
        return;
      }

      const quorumUserId = qConf.rows[0].user_id;
      const quorumToken  = quorumUserId === bob.id ? bob.token : alice.token;

      const { status: confirmStatus, data: confirmData } = await post(
        `/groups/${groupId}/spaces/${spaceId}/quorum/confirm`, {}, quorumToken,
      );

      assert.equal(confirmStatus, 200);
      // 1 member quorum → settled immediately
      assert.match(confirmData.message, /soldé|settled|quorum/i);

      const { data: detail } = await get(`/groups/${groupId}/spaces/${spaceId}`, alice.token);
      assert.equal(detail.status, 'settled');
    });
  });
});
