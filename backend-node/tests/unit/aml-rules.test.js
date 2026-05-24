/**
 * Unit tests for AML Decision Engine — Rules R1–R18
 * SplitEase AML Decision Table v2.0 / AMLD6 / FATF / PSD2
 *
 * Strategy: inject mock DB + FCM into require.cache BEFORE loading aml.js
 * so the module sees our controlled query responses.
 *
 * Thresholds (from aml.js config):
 *   sddThreshold      = 150   (below = SDD / low-risk)
 *   cddThreshold      = 10000 (above = EDD / CTR)
 *   highFrequencyCount = 10   (unusual pattern trigger)
 *   smurfingMinTransactions = 5
 *   autoSuspendAfterAlerts  = 3
 */

'use strict';

const { test, describe, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const path   = require('path');

// ── 1. Inject mocks into require.cache BEFORE loading aml.js ─────────────────
const DB_PATH  = path.resolve(__dirname, '../../src/config/db.js');
const FCM_PATH = path.resolve(__dirname, '../../src/services/fcm.js');

const mockDb  = require('../helpers/mock-db');
const mockFcm = require('../helpers/mock-fcm');

require.cache[DB_PATH]  = { id: DB_PATH,  filename: DB_PATH,  loaded: true, exports: mockDb };
require.cache[FCM_PATH] = { id: FCM_PATH, filename: FCM_PATH, loaded: true, exports: mockFcm };

// ── 2. Now safe to load aml.js ────────────────────────────────────────────────
const { preCheck } = require('../../src/services/aml');

// ── Scenario builder ──────────────────────────────────────────────────────────

const GROUP_ID = 'grp-test';
const USER_ID  = 'test-user-id';

/**
 * Run preCheck with the given user profile and transaction context.
 * Returns { result, blocked } where result = null means ALLOW.
 */
async function runRule(userOverrides, txOverrides = {}) {
  mockDb.resetCtx();
  mockFcm.clearCalls();

  const {
    amount       = 50,
    monthlyTotal = 0,
    highFreq     = 0,
    roundAmt     = 0,
    smurfing     = 0,
    alerts       = 0,
  } = txOverrides;

  mockDb.setCtx({
    user:          mockDb.makeUser(userOverrides),
    monthlyTotal,
    highFreqCount: highFreq,
    roundAmtCount: roundAmt,
    smurfingCount: smurfing,
    alertCount:    alerts,
  });

  const blocked = await preCheck(USER_ID, amount, GROUP_ID);
  return { blocked };
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('R1 — verified + KYC approved + amount < SDD threshold → ALLOW', () => {
  test('$50 monthly total < $150 → allowed', async () => {
    const { blocked } = await runRule({}, { amount: 50, monthlyTotal: 0 });
    assert.equal(blocked, null);
  });

  test('$100 total still below threshold → allowed', async () => {
    const { blocked } = await runRule({}, { amount: 100, monthlyTotal: 0 });
    assert.equal(blocked, null);
  });
});

describe('R2 — verified + KYC approved + CDD range ($150–$10k) → ALLOW', () => {
  test('$500 expense (total in CDD range) → allowed', async () => {
    const { blocked } = await runRule({}, { amount: 500, monthlyTotal: 0 });
    assert.equal(blocked, null);
  });

  test('$5000 expense → allowed', async () => {
    const { blocked } = await runRule({}, { amount: 5000, monthlyTotal: 0 });
    assert.equal(blocked, null);
  });

  test('$9999 expense → allowed', async () => {
    const { blocked } = await runRule({}, { amount: 9999, monthlyTotal: 0 });
    assert.equal(blocked, null);
  });
});

describe('R3 — PEP detected + KYC approved → EDD action (not blocked)', () => {
  test('PEP + verified + kyc approved → not blocked (EDD logged)', async () => {
    const { blocked } = await runRule(
      { is_pep: true },
      { amount: 200, monthlyTotal: 0 },
    );
    // EDD rule does not BLOCK — expense proceeds
    assert.equal(blocked, null);
  });
});

describe('R4 — sanctions list + KYC approved + not high-risk country → BLOCK', () => {
  test('sanctioned user → blocked', async () => {
    const { blocked } = await runRule(
      { on_sanctions_list: true, high_risk_country: false },
      { amount: 50 },
    );
    assert.notEqual(blocked, null);
    assert.match(blocked, /sanctions/i);
  });
});

describe('R5 — KYC incomplete + amount < SDD threshold → ALLOW (SDD exemption)', () => {
  test('unverified KYC + $50 → allowed under SDD', async () => {
    const { blocked } = await runRule(
      { kyc_status: 'pending' },
      { amount: 50, monthlyTotal: 0 },
    );
    assert.equal(blocked, null);
  });

  test('unverified KYC + $100 → allowed under SDD', async () => {
    const { blocked } = await runRule(
      { kyc_status: 'none' },
      { amount: 100, monthlyTotal: 0 },
    );
    assert.equal(blocked, null);
  });
});

describe('R6 — KYC incomplete + amount in CDD range → BLOCK', () => {
  test('no KYC + $500 → blocked', async () => {
    const { blocked } = await runRule(
      { kyc_status: 'pending' },
      { amount: 500, monthlyTotal: 0 },
    );
    assert.notEqual(blocked, null);
    assert.match(blocked, /kyc/i);
  });

  test('no KYC + $1000 → blocked', async () => {
    const { blocked } = await runRule(
      { kyc_status: 'none' },
      { amount: 1000, monthlyTotal: 0 },
    );
    assert.notEqual(blocked, null);
  });
});

describe('R7 — KYC approved + high-risk country → ALERT (not blocked)', () => {
  test('approved KYC + high-risk country → allowed (alert flagged)', async () => {
    const { blocked } = await runRule(
      { high_risk_country: true },
      { amount: 200, monthlyTotal: 0 },
    );
    // R7 triggers ALERT but not BLOCK
    assert.equal(blocked, null);
  });
});

describe('R8 — sanctions + high-risk country → BLOCK (freeze + SAR)', () => {
  test('sanctioned + high-risk country → blocked', async () => {
    const { blocked } = await runRule(
      { on_sanctions_list: true, high_risk_country: true },
      { amount: 50 },
    );
    assert.notEqual(blocked, null);
    assert.match(blocked, /sanctions/i);
  });
});

describe('R9 — unusual transaction pattern (any KYC level) → ALERT (not blocked)', () => {
  test('KYC approved + high frequency → ALERT, not blocked', async () => {
    const { blocked } = await runRule(
      {},  // verified + kyc approved
      { amount: 200, monthlyTotal: 0, highFreq: 10 },
    );
    // R9 → ALERT, expense should proceed
    assert.equal(blocked, null);
  });
});

describe('R10 — KYC approved + unusual + CDD range → ALERT (not blocked)', () => {
  test('KYC + unusual + $500 → ALERT, not blocked', async () => {
    const { blocked } = await runRule(
      {},
      { amount: 500, monthlyTotal: 0, highFreq: 10 },
    );
    assert.equal(blocked, null);
  });
});

describe('R11 — smurfing + unusual pattern → BLOCK (SAR)', () => {
  test('smurfing (5 small tx) + high frequency → blocked', async () => {
    const { blocked } = await runRule(
      {},  // verified + kyc approved (no PEP, no sanctions, no high-risk)
      { amount: 50, smurfing: 5, highFreq: 10 },
    );
    assert.notEqual(blocked, null);
    assert.match(blocked, /smurfing/i);
  });
});

describe('R12 — PEP + smurfing + unusual → BLOCK (freeze + SAR + EDD)', () => {
  test('PEP + smurfing + high frequency → blocked (max risk)', async () => {
    const { blocked } = await runRule(
      { is_pep: true, on_sanctions_list: false, high_risk_country: false },
      { amount: 50, smurfing: 5, highFreq: 10 },
    );
    assert.notEqual(blocked, null);
    assert.match(blocked, /pep/i);
  });
});

describe('R13 — KYC incomplete + below SDD + unusual → blocked by R15', () => {
  // Note: R15 fires before R13 in the rule chain for any unusual+no-kyc case.
  // The engine catches this under R15 ("Schéma suspect + EDD requis").
  test('no KYC + $50 + unusual → blocked (R15 fires before R13)', async () => {
    const { blocked } = await runRule(
      { kyc_status: 'pending' },
      { amount: 50, highFreq: 10, monthlyTotal: 0 },
    );
    assert.notEqual(blocked, null);
  });
});

describe('R14 — KYC incomplete + CDD range + unusual → blocked by R15', () => {
  // R15 fires before R14 — engine returns R15 reason.
  test('no KYC + $500 + unusual → blocked (R15 fires before R14)', async () => {
    const { blocked } = await runRule(
      { kyc_status: 'pending' },
      { amount: 500, highFreq: 10, monthlyTotal: 0 },
    );
    assert.notEqual(blocked, null);
  });
});

describe('R15 — unusual pattern + KYC incomplete (no high-risk) → BLOCK', () => {
  test('no KYC + high freq + not high-risk-country → blocked', async () => {
    const { blocked } = await runRule(
      { kyc_status: 'pending', high_risk_country: false, on_sanctions_list: false, is_pep: false },
      { amount: 200, highFreq: 10 },
    );
    assert.notEqual(blocked, null);
    assert.match(blocked, /schéma suspect|edd/i);
  });
});

describe('R16 — unusual + KYC incomplete + high-risk country → BLOCK (freeze + SAR)', () => {
  test('no KYC + high freq + high-risk-country → blocked', async () => {
    const { blocked } = await runRule(
      { kyc_status: 'pending', high_risk_country: true, on_sanctions_list: false, is_pep: false },
      { amount: 200, highFreq: 10 },
    );
    assert.notEqual(blocked, null);
    assert.match(blocked, /risque|risk/i);
  });
});

describe('R17 — sanctions + KYC incomplete → BLOCK (freeze + SAR)', () => {
  test('sanctions + no kyc → blocked', async () => {
    const { blocked } = await runRule(
      { on_sanctions_list: true, kyc_status: 'pending' },
      { amount: 50 },
    );
    assert.notEqual(blocked, null);
    assert.match(blocked, /sanctions/i);
  });
});

describe('R18 — account not verified → BLOCK (systemic)', () => {
  test('is_verified=false → blocked regardless of amount', async () => {
    const { blocked } = await runRule(
      { is_verified: false },
      { amount: 10 },
    );
    assert.notEqual(blocked, null);
    assert.match(blocked, /vérifié|verifié|verified/i);
  });

  test('unverified + small amount still blocked', async () => {
    const { blocked } = await runRule(
      { is_verified: false, kyc_status: 'approved' },
      { amount: 1 },
    );
    assert.notEqual(blocked, null);
  });
});

describe('R18 — user not found → BLOCK', () => {
  test('null user row → blocked', async () => {
    mockDb.resetCtx();
    mockFcm.clearCalls();
    mockDb.setCtx({ user: null });
    const blocked = await preCheck(USER_ID, 50, GROUP_ID);
    assert.notEqual(blocked, null);
  });
});

describe('EDD — amount > $10,000 → BLOCK (CTR required)', () => {
  test('$10,001 (single amount > cddThreshold) → blocked', async () => {
    const { blocked } = await runRule(
      {},  // verified + kyc approved
      { amount: 10001, monthlyTotal: 0 },
    );
    assert.notEqual(blocked, null);
    assert.match(blocked, /edd|seuil|threshold/i);
  });

  test('exactly $10,000 → allowed (edge: inclusive in CDD range)', async () => {
    const { blocked } = await runRule(
      {},
      { amount: 10000, monthlyTotal: 0 },
    );
    assert.equal(blocked, null);
  });
});

describe('Auto-suspension escalation — 3+ pending alerts → suspend', () => {
  test('3 existing alerts + new BLOCK → account auto-suspended (FCM notified)', async () => {
    // Use a BLOCK rule (R6: no KYC + CDD amount) so preCheck awaits applyActions fully.
    // alertCount=3 means checkEscalation triggers the suspension path.
    mockDb.resetCtx();
    mockFcm.clearCalls();
    mockDb.setCtx({
      user:          mockDb.makeUser({ kyc_status: 'pending', aml_status: 'clear' }),
      monthlyTotal:  0,
      highFreqCount: 0,
      alertCount:    3,   // >= autoSuspendAfterAlerts (3) → triggers suspension
    });

    // R6 fires: no KYC + $500 (CDD range) → BLOCK → applyActions is awaited
    await preCheck(USER_ID, 500, GROUP_ID);

    // checkEscalation runs synchronously inside applyActions and calls notifyAdmins
    const fcmCalls = mockFcm.getCalls();
    const adminCall = fcmCalls.find(c => c.target === 'admins');
    assert.ok(adminCall, 'Admin should be notified of auto-suspension');
  });
});

describe('Round-amount unusual pattern (smurfing variant)', () => {
  test('$200 (round) + 3 previous round amounts → unusual detected → ALERT', async () => {
    const { blocked } = await runRule(
      {},  // verified + kyc approved
      { amount: 200, monthlyTotal: 0, roundAmt: 3 },
    );
    // R9 ALERT triggered but not blocked
    assert.equal(blocked, null);
  });

  test('$200 round + 3 round amounts + smurfing → blocked (R11)', async () => {
    const { blocked } = await runRule(
      {},
      { amount: 200, roundAmt: 3, smurfing: 5 },
    );
    assert.notEqual(blocked, null);
  });
});

describe('Rule priority order (ensure correct precedence)', () => {
  test('R4 fires before R7: sanctions always beats high-risk-country alert', async () => {
    const { blocked } = await runRule(
      { on_sanctions_list: true, high_risk_country: true, kyc_status: 'approved' },
      { amount: 200 },
    );
    // Should hit R8 (sanctions + high-risk), not just R7 alert
    assert.notEqual(blocked, null);
  });

  test('R12 fires before R11: PEP+smurfing+unusual is more severe than plain smurfing', async () => {
    const { blocked } = await runRule(
      { is_pep: true, on_sanctions_list: false, high_risk_country: false },
      { amount: 50, smurfing: 5, highFreq: 10 },
    );
    assert.notEqual(blocked, null);
    assert.match(blocked, /pep/i);
  });

  test('R16 fires before R15: unusual+no-KYC+high-risk is more severe than just unusual+no-KYC', async () => {
    const { blocked } = await runRule(
      { kyc_status: 'pending', high_risk_country: true, on_sanctions_list: false, is_pep: false },
      { amount: 200, highFreq: 10 },
    );
    assert.notEqual(blocked, null);
    // R16 message mentions risk factors
    assert.match(blocked, /risque|risk/i);
  });

  test('R18 fires before everything: unverified always blocked', async () => {
    const { blocked } = await runRule(
      { is_verified: false, on_sanctions_list: true, is_pep: true, high_risk_country: true },
      { amount: 50, smurfing: 5, highFreq: 10 },
    );
    assert.notEqual(blocked, null);
    assert.match(blocked, /vérifié|verified/i);
  });
});
