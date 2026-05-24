const express = require('express');
const { v4: uuidv4 } = require('uuid');
const { query } = require('../config/db');
const { authenticate } = require('../middleware/auth');

const router = express.Router();
router.use(authenticate);

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
router.param('groupId',   (req, res, next, val) => { if (!UUID_RE.test(val)) return res.status(400).json({ message: `Invalid group ID: "${val}"` });   next(); });
router.param('expenseId', (req, res, next, val) => { if (!UUID_RE.test(val)) return res.status(400).json({ message: `Invalid expense ID: "${val}"` }); next(); });

// POST /groups/:groupId/expenses
router.post('/groups/:groupId/expenses', async (req, res) => {
  const { groupId } = req.params;
  const { title, amount, paidBy, splitMode = 'equally', category = 'other', participants } = req.body;

  if (!title?.trim()) return res.status(400).json({ message: 'Title is required' });
  if (!amount || amount <= 0) return res.status(400).json({ message: 'Amount must be positive' });
  if (!participants?.length) return res.status(400).json({ message: 'At least one participant required' });

  const resolvedParticipants = resolveShares(splitMode, amount, participants);
  if (!resolvedParticipants)
    return res.status(400).json({ message: 'splitMode must be equally, exact, or percentage' });

  if (category !== 'settlement') {
    const { preCheck } = require('../services/aml');
    const violation = await preCheck(paidBy, amount, groupId);
    if (violation)
      return res.status(422).json({ message: `Expense blocked by compliance rules: ${violation}` });
  }

  const expenseId = uuidv4();
  await query(
    'INSERT INTO expenses (id, group_id, title, amount, paid_by, split_mode, category, created_at) VALUES ($1,$2,$3,$4,$5,$6,$7,NOW())',
    [expenseId, groupId, title, amount, paidBy, splitMode, category]
  );
  for (const [userId, share] of resolvedParticipants) {
    await query(
      'INSERT INTO expense_participants (id, expense_id, user_id, share) VALUES ($1,$2,$3,$4)',
      [uuidv4(), expenseId, userId, share]
    );
  }

  const paidByName = await getUserName(paidBy);
  await logActivity(expenseId, req.userId, 'created',
    `Added "${title}" — $${parseFloat(amount).toFixed(2)} paid by ${paidByName}, split ${splitMode} between ${resolvedParticipants.length} participant(s)`);

  const expense = await getExpenseById(expenseId, req.userId);
  res.status(201).json(expense);

  if (category !== 'settlement') {
    const nonPayers = resolvedParticipants.map(([uid]) => uid).filter(uid => uid !== paidBy);
    const { notifyUser } = require('../services/fcm');
    const { checkExpense } = require('../services/aml');
    for (const participantId of nonPayers) {
      await query(
        'INSERT INTO expense_invitations (id, expense_id, group_id, invited_user_id, invited_by_id, status, created_at) VALUES ($1,$2,$3,$4,$5,$6,NOW())',
        [uuidv4(), expenseId, groupId, participantId, paidBy, 'pending']
      ).catch(() => {});
      notifyUser(participantId, 'New expense — consent required',
        `${paidByName} added you to "${title}" (${parseFloat(amount).toFixed(2)})`,
        { type: 'expense_invitation', expenseId }).catch(() => {});
    }
    checkExpense(expenseId, paidBy, amount, groupId).catch(() => {});
  }
});

// GET /groups/:groupId/expenses
router.get('/groups/:groupId/expenses', async (req, res) => {
  const { groupId } = req.params;
  const [expenses, pendingInvs] = await Promise.all([
    query(
      'SELECT * FROM expenses WHERE group_id = $1 ORDER BY created_at DESC',
      [groupId]
    ),
    query(
      'SELECT * FROM expense_invitations WHERE invited_user_id = $1 AND status = $2',
      [req.userId, 'pending']
    ),
  ]);

  const pendingByExpense = Object.fromEntries(pendingInvs.rows.map(i => [i.expense_id, i.id]));

  const result = await Promise.all(expenses.rows.map(async e => {
    const full = await buildExpenseResponse(e, req.userId);
    return { ...full, pendingInvitationId: pendingByExpense[e.id] || null };
  }));

  res.json(result);
});

// GET /groups/:groupId/expenses/:expenseId
router.get('/groups/:groupId/expenses/:expenseId', async (req, res) => {
  const { expenseId } = req.params;
  const expResult = await query('SELECT * FROM expenses WHERE id = $1', [expenseId]);
  const expense = expResult.rows[0];
  if (!expense) return res.status(404).json({ message: 'Expense not found' });

  const [full, activities, pendingInv] = await Promise.all([
    buildExpenseResponse(expense, req.userId),
    query(
      `SELECT ea.*, u.name AS user_name FROM expense_activities ea
       JOIN users u ON u.id = ea.user_id WHERE ea.expense_id = $1 ORDER BY ea.created_at ASC`,
      [expenseId]
    ),
    query(
      'SELECT id FROM expense_invitations WHERE expense_id = $1 AND invited_user_id = $2 AND status = $3',
      [expenseId, req.userId, 'pending']
    ),
  ]);

  res.json({
    expense: full,
    activities: activities.rows.map(a => ({
      id: a.id, userId: a.user_id, userName: a.user_name,
      action: a.action, details: a.details, createdAt: a.created_at,
    })),
    pendingInvitationId: pendingInv.rows[0]?.id || null,
  });
});

// PATCH /groups/:groupId/expenses/:expenseId
router.patch('/groups/:groupId/expenses/:expenseId', async (req, res) => {
  const { groupId, expenseId } = req.params;
  const { title, amount, paidBy, splitMode = 'equally', category = 'other', participants } = req.body;

  if (!title?.trim()) return res.status(400).json({ message: 'Title is required' });
  if (!amount || amount <= 0) return res.status(400).json({ message: 'Amount must be positive' });

  const resolvedParticipants = resolveShares(splitMode, amount, participants);
  if (!resolvedParticipants) return res.status(400).json({ message: 'Invalid splitMode' });

  const prevResult = await query('SELECT * FROM expenses WHERE id = $1', [expenseId]);
  const prev = prevResult.rows[0];

  const result = await query(
    'UPDATE expenses SET title=$1, amount=$2, paid_by=$3, split_mode=$4, category=$5, updated_at=NOW() WHERE id=$6',
    [title, amount, paidBy, splitMode, category, expenseId]
  );
  if (!result.rowCount) return res.status(404).json({ message: 'Expense not found' });

  await query('DELETE FROM expense_participants WHERE expense_id = $1', [expenseId]);
  for (const [userId, share] of resolvedParticipants) {
    await query(
      'INSERT INTO expense_participants (id, expense_id, user_id, share) VALUES ($1,$2,$3,$4)',
      [uuidv4(), expenseId, userId, share]
    );
  }

  const changes = [];
  if (prev) {
    if (prev.title !== title) changes.push(`title: "${prev.title}" → "${title}"`);
    if (parseFloat(prev.amount) !== parseFloat(amount)) changes.push(`amount: ${prev.amount} → ${amount}`);
    if (prev.split_mode !== splitMode) changes.push(`split: ${prev.split_mode} → ${splitMode}`);
  }
  const details = changes.length ? changes.join(', ') : 'Updated expense';
  await logActivity(expenseId, req.userId, 'updated', details);

  const updated = await getExpenseById(expenseId, req.userId);
  res.json(updated);

  const { notifyGroupMembers } = require('../services/fcm');
  const membersResult = await query('SELECT user_id FROM group_members WHERE group_id = $1', [groupId]);
  const memberIds = membersResult.rows.map(m => m.user_id);
  notifyGroupMembers(groupId, req.userId, memberIds, 'Expense updated', `"${title}" was edited`).catch(() => {});
});

// DELETE /groups/:groupId/expenses/:expenseId
router.delete('/groups/:groupId/expenses/:expenseId', async (req, res) => {
  const { groupId, expenseId } = req.params;
  const expResult = await query('SELECT title FROM expenses WHERE id = $1', [expenseId]);
  const expense = expResult.rows[0];

  await query('DELETE FROM expense_participants WHERE expense_id = $1', [expenseId]);
  const result = await query('DELETE FROM expenses WHERE id = $1', [expenseId]);

  if (!result.rowCount) return res.status(404).json({ message: 'Expense not found' });
  res.json({ message: 'Expense deleted' });

  if (expense) {
    const { notifyGroupMembers } = require('../services/fcm');
    const membersResult = await query('SELECT user_id FROM group_members WHERE group_id = $1', [groupId]);
    const memberIds = membersResult.rows.map(m => m.user_id);
    notifyGroupMembers(groupId, req.userId, memberIds, 'Expense deleted', `"${expense.title}" was removed`).catch(() => {});
  }
});

// GET /groups/:groupId/balances
router.get('/groups/:groupId/balances', async (req, res) => {
  const { groupId } = req.params;
  const balances = await calculateBalances(groupId);
  res.json(balances.map(b => ({ userId: b.userId, name: b.name, amount: b.amount })));
});

// GET /groups/:groupId/settlements
router.get('/groups/:groupId/settlements', async (req, res) => {
  const { groupId } = req.params;
  const settlements = await calculateSettlements(groupId);
  res.json(settlements);
});

// ── Helpers ─────────────────────────────────────────────────────────────────

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

async function getUserName(userId) {
  const r = await query('SELECT name FROM users WHERE id = $1', [userId]);
  return r.rows[0]?.name || 'Unknown';
}

async function logActivity(expenseId, userId, action, details) {
  await query(
    'INSERT INTO expense_activities (id, expense_id, user_id, action, details, created_at) VALUES ($1,$2,$3,$4,$5,NOW())',
    [uuidv4(), expenseId, userId, action, details]
  );
}

async function buildExpenseResponse(expense, currentUserId) {
  const participants = await query(
    'SELECT ep.user_id, ep.share, u.name FROM expense_participants ep JOIN users u ON u.id = ep.user_id WHERE ep.expense_id = $1',
    [expense.id]
  );
  const paidByName = await getUserName(expense.paid_by);
  return {
    id: expense.id,
    groupId: expense.group_id,
    title: expense.title,
    amount: parseFloat(expense.amount),
    paidBy: expense.paid_by,
    paidByName,
    splitMode: expense.split_mode,
    category: expense.category,
    participants: participants.rows.map(p => ({ userId: p.user_id, name: p.name, share: parseFloat(p.share) })),
    createdAt: expense.created_at,
    updatedAt: expense.updated_at || null,
  };
}

async function getExpenseById(expenseId, currentUserId) {
  const r = await query('SELECT * FROM expenses WHERE id = $1', [expenseId]);
  if (!r.rows[0]) return null;
  return buildExpenseResponse(r.rows[0], currentUserId);
}

async function calculateBalances(groupId) {
  const [paidResult, owedResult] = await Promise.all([
    query(
      `SELECT e.paid_by AS user_id, u.name, SUM(e.amount) AS total
       FROM expenses e JOIN users u ON u.id = e.paid_by
       WHERE e.group_id = $1 GROUP BY e.paid_by, u.name`,
      [groupId]
    ),
    query(
      `SELECT ep.user_id, u.name, SUM(ep.share) AS total
       FROM expense_participants ep
       JOIN expenses e ON e.id = ep.expense_id
       JOIN users u ON u.id = ep.user_id
       WHERE e.group_id = $1 GROUP BY ep.user_id, u.name`,
      [groupId]
    ),
  ]);

  const net   = {};
  const names = {};

  for (const row of paidResult.rows) {
    net[row.user_id]   = (net[row.user_id] || 0) + parseFloat(row.total);
    names[row.user_id] = row.name;
  }
  for (const row of owedResult.rows) {
    net[row.user_id]   = (net[row.user_id] || 0) - parseFloat(row.total);
    names[row.user_id] = row.name;
  }

  return Object.entries(net)
    .map(([userId, amount]) => ({ userId, name: names[userId] || 'Unknown', amount: Math.round(amount * 100) / 100 }))
    .sort((a, b) => b.amount - a.amount);
}

async function calculateSettlements(groupId) {
  const balances = await calculateBalances(groupId);
  const creditors = balances.filter(b => b.amount > 0).sort((a, b) => b.amount - a.amount);
  const debtors   = balances.filter(b => b.amount < 0).sort((a, b) => a.amount - b.amount);

  const cAmounts = creditors.map(c => c.amount);
  const dAmounts = debtors.map(d => -d.amount);
  const settlements = [];
  let ci = 0, di = 0;

  while (ci < creditors.length && di < debtors.length) {
    const settle = Math.min(cAmounts[ci], dAmounts[di]);
    settlements.push({
      fromUserId: debtors[di].userId,
      fromName:   debtors[di].name,
      toUserId:   creditors[ci].userId,
      toName:     creditors[ci].name,
      amount:     Math.round(settle * 100) / 100,
    });
    cAmounts[ci] -= settle;
    dAmounts[di] -= settle;
    if (cAmounts[ci] === 0) ci++;
    if (dAmounts[di] === 0) di++;
  }

  return settlements;
}

module.exports = router;
