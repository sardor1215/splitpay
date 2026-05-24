/**
 * Unit tests for pure business-logic functions.
 * No DB, no network — runs offline.
 *
 * Covers:
 *   - resolveShares  (expense splitting: equally / exact / percentage)
 *   - calculateBalances logic (fixed two-query approach)
 *   - requiredQuorum (Espaces FR-058)
 *   - recalculateShares (Espaces FR-054 force-launch)
 */

'use strict';

const { test, describe } = require('node:test');
const assert = require('node:assert/strict');

// ── Pure functions duplicated from production code ───────────────────────────

function resolveShares(splitMode, totalAmount, participants) {
  const total = parseFloat(totalAmount);
  const count = participants.length;
  if (splitMode === 'equally') {
    const share = Math.round((total / count) * 100) / 100;
    return participants.map(p => [p.userId, share]);
  }
  if (splitMode === 'exact') {
    return participants.map(p => [p.userId, parseFloat(p.share || 0)]);
  }
  if (splitMode === 'percentage') {
    return participants.map(p => [p.userId, Math.round(total * parseFloat(p.share || 0) / 100 * 100) / 100]);
  }
  return null;
}

function requiredQuorum(memberCount) {
  if (memberCount <= 2)  return 1;
  if (memberCount <= 5)  return 2;
  if (memberCount <= 10) return 4;
  return Math.floor(memberCount / 3);
}

function recalculateShares(totalAmount, acceptedCount, splitMode, originalShares) {
  if (splitMode === 'equally') {
    const share = Math.round((totalAmount / acceptedCount) * 100) / 100;
    return Object.fromEntries(originalShares.map(s => [s.user_id, share]));
  }
  const totalShares = originalShares.reduce((s, p) => s + parseFloat(p.share), 0);
  return Object.fromEntries(originalShares.map(s => [
    s.user_id,
    Math.round((parseFloat(s.share) / totalShares * totalAmount) * 100) / 100,
  ]));
}

// Simulates calculateBalances with the fixed two-query logic
function simulateBalances(expenses, participants) {
  // expenses: [{ paid_by, amount }]
  // participants: [{ expense_id, user_id, share }]
  const paidMap = {};
  const owedMap = {};

  for (const e of expenses) {
    paidMap[e.paid_by] = (paidMap[e.paid_by] || 0) + parseFloat(e.amount);
  }
  for (const p of participants) {
    owedMap[p.user_id] = (owedMap[p.user_id] || 0) + parseFloat(p.share);
  }

  const allIds = new Set([...Object.keys(paidMap), ...Object.keys(owedMap)]);
  const net = {};
  for (const id of allIds) {
    net[id] = Math.round(((paidMap[id] || 0) - (owedMap[id] || 0)) * 100) / 100;
  }
  return net;
}

// ── resolveShares ────────────────────────────────────────────────────────────

describe('resolveShares — equally', () => {
  test('2 participants, $100 → $50 each', () => {
    const result = resolveShares('equally', 100, [
      { userId: 'A' }, { userId: 'B' },
    ]);
    assert.equal(result.length, 2);
    assert.equal(result[0][1], 50);
    assert.equal(result[1][1], 50);
  });

  test('3 participants, $100 → $33.33 each', () => {
    const result = resolveShares('equally', 100, [
      { userId: 'A' }, { userId: 'B' }, { userId: 'C' },
    ]);
    assert.equal(result.length, 3);
    result.forEach(([, share]) => assert.equal(share, 33.33));
  });

  test('5 participants (odd count), $50 → $10 each', () => {
    const pts = ['A','B','C','D','E'].map(id => ({ userId: id }));
    const result = resolveShares('equally', 50, pts);
    assert.equal(result.length, 5);
    result.forEach(([, share]) => assert.equal(share, 10));
  });

  test('7 participants (odd count), $70 → $10 each', () => {
    const pts = ['A','B','C','D','E','F','G'].map(id => ({ userId: id }));
    const result = resolveShares('equally', 70, pts);
    result.forEach(([, share]) => assert.equal(share, 10));
  });

  test('1 participant, $200 → $200', () => {
    const result = resolveShares('equally', 200, [{ userId: 'A' }]);
    assert.equal(result[0][1], 200);
  });

  test('unknown splitMode returns null', () => {
    const result = resolveShares('random', 100, [{ userId: 'A' }]);
    assert.equal(result, null);
  });
});

describe('resolveShares — exact', () => {
  test('exact shares are taken as-is', () => {
    const result = resolveShares('exact', 90, [
      { userId: 'A', share: '40' },
      { userId: 'B', share: '50' },
    ]);
    assert.equal(result[0][1], 40);
    assert.equal(result[1][1], 50);
  });

  test('missing share defaults to 0', () => {
    const result = resolveShares('exact', 100, [{ userId: 'A' }]);
    assert.equal(result[0][1], 0);
  });
});

describe('resolveShares — percentage', () => {
  test('50% / 50% of $200 → $100 each', () => {
    const result = resolveShares('percentage', 200, [
      { userId: 'A', share: '50' },
      { userId: 'B', share: '50' },
    ]);
    assert.equal(result[0][1], 100);
    assert.equal(result[1][1], 100);
  });

  test('30% / 70% of $100 → $30 / $70', () => {
    const result = resolveShares('percentage', 100, [
      { userId: 'A', share: '30' },
      { userId: 'B', share: '70' },
    ]);
    assert.equal(result[0][1], 30);
    assert.equal(result[1][1], 70);
  });

  test('33.33% × 3 of $99 → rounds to $33 each', () => {
    const pts = ['A','B','C'].map(id => ({ userId: id, share: '33.33' }));
    const result = resolveShares('percentage', 99, pts);
    // 99 * 33.33 / 100 = 32.9967 → Math.round → 33.00
    result.forEach(([, share]) => assert.equal(share, 33));
  });
});

// ── calculateBalances (fixed logic) ──────────────────────────────────────────

describe('calculateBalances — net positions', () => {
  test('A pays $90 for A + B equally → A: +45, B: -45', () => {
    const net = simulateBalances(
      [{ paid_by: 'A', amount: 90 }],
      [{ user_id: 'A', share: 45 }, { user_id: 'B', share: 45 }],
    );
    assert.equal(net['A'], 45);
    assert.equal(net['B'], -45);
  });

  test('A pays $100 for 3 people equally → A: +66.67, B: -33.33, C: -33.33', () => {
    const net = simulateBalances(
      [{ paid_by: 'A', amount: 100 }],
      [
        { user_id: 'A', share: 33.33 },
        { user_id: 'B', share: 33.33 },
        { user_id: 'C', share: 33.33 },
      ],
    );
    assert.equal(net['A'], 66.67);
    assert.equal(net['B'], -33.33);
    assert.equal(net['C'], -33.33);
  });

  test('Bug regression: payer amount not multiplied by participant count', () => {
    // 3 participants: payer amount must be added ONCE, not 3 times
    const net = simulateBalances(
      [{ paid_by: 'A', amount: 300 }],
      [
        { user_id: 'A', share: 100 },
        { user_id: 'B', share: 100 },
        { user_id: 'C', share: 100 },
      ],
    );
    // A paid 300, owes 100 → net +200
    assert.equal(net['A'], 200);
    assert.equal(net['B'], -100);
    assert.equal(net['C'], -100);
  });

  test('Multiple expenses, different payers', () => {
    const net = simulateBalances(
      [
        { paid_by: 'A', amount: 60 },
        { paid_by: 'B', amount: 60 },
      ],
      [
        { user_id: 'A', share: 30 }, { user_id: 'B', share: 30 },  // expense 1
        { user_id: 'A', share: 30 }, { user_id: 'B', share: 30 },  // expense 2
      ],
    );
    // A paid 60, owes 60 → 0; B paid 60, owes 60 → 0
    assert.equal(net['A'], 0);
    assert.equal(net['B'], 0);
  });

  test('Settlement zeros out the balance', () => {
    // A owes B $50; B adds a settlement expense where B pays $50 for A
    const net = simulateBalances(
      [
        { paid_by: 'B', amount: 100 }, // original expense
        { paid_by: 'A', amount: 50 },  // A settles up
      ],
      [
        { user_id: 'A', share: 50 }, { user_id: 'B', share: 50 }, // expense 1
        { user_id: 'B', share: 50 },                               // settlement to B
      ],
    );
    // A: paid 50, owed 50 → 0; B: paid 100, owed 100 → 0
    assert.equal(net['A'], 0);
    assert.equal(net['B'], 0);
  });
});

// ── requiredQuorum (FR-058) ───────────────────────────────────────────────────

describe('requiredQuorum — FR-058', () => {
  test('1 member → quorum 1', () => assert.equal(requiredQuorum(1), 1));
  test('2 members → quorum 1', () => assert.equal(requiredQuorum(2), 1));
  test('3 members → quorum 2', () => assert.equal(requiredQuorum(3), 2));
  test('5 members → quorum 2', () => assert.equal(requiredQuorum(5), 2));
  test('6 members → quorum 4', () => assert.equal(requiredQuorum(6), 4));
  test('10 members → quorum 4', () => assert.equal(requiredQuorum(10), 4));
  test('11 members → quorum 3 (floor(11/3))', () => assert.equal(requiredQuorum(11), 3));
  test('15 members → quorum 5 (floor(15/3))', () => assert.equal(requiredQuorum(15), 5));
  test('30 members → quorum 10', () => assert.equal(requiredQuorum(30), 10));
});

// ── recalculateShares (FR-054 force-launch) ───────────────────────────────────

describe('recalculateShares — FR-054 force-launch', () => {
  test('equally: 3 of 5 accept $150 → $50 each', () => {
    const accepted = [
      { user_id: 'A', share: '50' },
      { user_id: 'B', share: '50' },
      { user_id: 'C', share: '50' },
    ];
    const shares = recalculateShares(150, 3, 'equally', accepted);
    assert.equal(shares['A'], 50);
    assert.equal(shares['B'], 50);
    assert.equal(shares['C'], 50);
  });

  test('equally: 2 of 4 accept $200 → $100 each', () => {
    const accepted = [
      { user_id: 'A', share: '50' },
      { user_id: 'B', share: '50' },
    ];
    const shares = recalculateShares(200, 2, 'equally', accepted);
    assert.equal(shares['A'], 100);
    assert.equal(shares['B'], 100);
  });

  test('exact: proportional redistribution when 1 of 2 declines', () => {
    // A had 60, B had 40 (total 100). Only A accepted.
    // B's share (40) is redistributed proportionally → A gets all $100.
    const accepted = [{ user_id: 'A', share: '60' }];
    const shares = recalculateShares(100, 1, 'exact', accepted);
    assert.equal(shares['A'], 100);
  });

  test('exact: 2 accept with different original shares → proportional', () => {
    // A had 60, B had 40 (total 100). C declined (had 30).
    // New total still $100 → A: 60/(60+40)*100 = 60, B: 40/(60+40)*100 = 40
    const accepted = [
      { user_id: 'A', share: '60' },
      { user_id: 'B', share: '40' },
    ];
    const shares = recalculateShares(100, 2, 'exact', accepted);
    assert.equal(shares['A'], 60);
    assert.equal(shares['B'], 40);
  });
});
