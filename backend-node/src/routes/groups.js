const express = require('express');
const { v4: uuidv4 } = require('uuid');
const { query } = require('../config/db');
const { authenticate } = require('../middleware/auth');

const router = express.Router();
router.use(authenticate);

// ── UUID guard — reject non-UUID :groupId before any handler runs ────────────
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
router.param('groupId', (req, res, next, val) => {
  if (!UUID_RE.test(val)) return res.status(400).json({ message: `Invalid group ID format: "${val}"` });
  next();
});

// ── Groups ──────────────────────────────────────────────────────────────────

// POST /groups
router.post('/', async (req, res) => {
  const { name, emoji = '💰', description } = req.body;
  if (!name?.trim()) return res.status(400).json({ message: 'Group name is required' });

  const groupId = uuidv4();
  const token   = uuidv4();
  await query(
    'INSERT INTO expense_groups (id, name, emoji, description, created_by, invite_token, created_at) VALUES ($1,$2,$3,$4,$5,$6,NOW())',
    [groupId, name, emoji, description || null, req.userId, token]
  );
  await query(
    'INSERT INTO group_members (id, group_id, user_id, role, joined_at) VALUES ($1,$2,$3,$4,NOW())',
    [uuidv4(), groupId, req.userId, 'admin']
  );

  const group = await getGroupById(groupId);
  res.status(201).json(toGroupResponse(group, 1, new Date().toISOString(), 0));
});

// GET /groups
router.get('/', async (req, res) => {
  const result = await query(
    `SELECT g.* FROM expense_groups g
     JOIN group_members m ON m.group_id = g.id
     WHERE m.user_id = $1 AND g.is_archived = false`,
    [req.userId]
  );
  const groups = result.rows;
  if (!groups.length) return res.json([]);

  const ids = groups.map(g => g.id);
  const [counts, lastActs, balances] = await Promise.all([
    getMemberCounts(ids),
    getLastActivities(ids),
    getUserBalancesForGroups(ids, req.userId),
  ]);

  const sorted = groups.sort((a, b) => {
    const aDate = lastActs[a.id] || a.created_at;
    const bDate = lastActs[b.id] || b.created_at;
    return new Date(bDate) - new Date(aDate);
  });

  res.json(sorted.map(g => toGroupResponse(
    g,
    counts[g.id] || 1,
    lastActs[g.id] || g.created_at,
    balances[g.id] || 0
  )));
});

// GET /groups/:groupId
router.get('/:groupId', async (req, res) => {
  const { groupId } = req.params;
  const group = await getGroupById(groupId);
  if (!group) return res.status(404).json({ message: 'Group not found' });

  if (!(await isMember(groupId, req.userId)))
    return res.status(403).json({ message: 'You are not a member of this group' });

  const count = await getMemberCount(groupId);
  res.json(toGroupResponse(group, count, group.created_at, 0));
});

// PATCH /groups/:groupId
router.patch('/:groupId', async (req, res) => {
  const { groupId } = req.params;
  if (!(await isMember(groupId, req.userId)))
    return res.status(403).json({ message: 'Not a member' });

  const { name, emoji, description } = req.body;
  const fields = ['name = $1'];
  const values = [name];
  let i = 2;
  if (emoji !== undefined)       { fields.push(`emoji = $${i++}`);       values.push(emoji); }
  if (description !== undefined) { fields.push(`description = $${i++}`); values.push(description); }

  const result = await query(
    `UPDATE expense_groups SET ${fields.join(', ')} WHERE id = $${i}`,
    [...values, groupId]
  );
  if (!result.rowCount) return res.status(404).json({ message: 'Group not found' });

  const group = await getGroupById(groupId);
  const count = await getMemberCount(groupId);
  res.json(toGroupResponse(group, count, group.created_at, 0));

  const { notifyGroupMembers } = require('../services/fcm');
  const memberIds = (await getMembers(groupId)).map(m => m.user_id);
  notifyGroupMembers(groupId, req.userId, memberIds, 'Group updated', `"${name}" has been renamed`).catch(() => {});
});

// GET /groups/:groupId/members
router.get('/:groupId/members', async (req, res) => {
  const { groupId } = req.params;
  if (!(await isMember(groupId, req.userId)))
    return res.status(403).json({ message: 'Not a member' });

  const members = await getMembers(groupId);
  res.json(members.map(m => ({ userId: m.user_id, name: m.name, role: m.role, joinedAt: m.joined_at })));
});

// GET /groups/:groupId/invite
router.get('/:groupId/invite', async (req, res) => {
  const { groupId } = req.params;
  if (!(await isAdmin(groupId, req.userId)))
    return res.status(403).json({ message: 'Only admins can view the invite link' });

  const group = await getGroupById(groupId);
  if (!group) return res.status(404).json({ message: 'Group not found' });

  const baseUrl = process.env.BASE_URL || 'http://localhost:3000';
  res.json({ inviteLink: `${baseUrl}/groups/join/${group.invite_token}` });
});

// POST /groups/:groupId/invite/regenerate
router.post('/:groupId/invite/regenerate', async (req, res) => {
  const { groupId } = req.params;
  if (!(await isAdmin(groupId, req.userId)))
    return res.status(403).json({ message: 'Only admins can regenerate the invite link' });

  const token = uuidv4();
  await query('UPDATE expense_groups SET invite_token = $1 WHERE id = $2', [token, groupId]);
  const baseUrl = process.env.BASE_URL || 'http://localhost:3000';
  res.json({ inviteLink: `${baseUrl}/groups/join/${token}` });
});

// POST /groups/:groupId/members
router.post('/:groupId/members', async (req, res) => {
  const { groupId } = req.params;
  const { userId: memberId } = req.body;

  if (!(await isAdmin(groupId, req.userId)))
    return res.status(403).json({ message: 'Only admins can add members' });

  if (await isMember(groupId, memberId))
    return res.status(409).json({ message: 'User is already a member' });

  const pendingResult = await query(
    'SELECT id FROM group_invitations WHERE group_id = $1 AND invited_user_id = $2 AND status = $3',
    [groupId, memberId, 'pending']
  );
  if (pendingResult.rows.length)
    return res.status(409).json({ message: 'An invitation is already pending for this user' });

  const targetResult = await query('SELECT * FROM users WHERE id = $1 AND is_deleted = false', [memberId]);
  const targetUser = targetResult.rows[0];
  if (!targetUser) return res.status(404).json({ message: 'User not found' });

  if (targetUser.require_consent) {
    const invId = uuidv4();
    await query(
      'INSERT INTO group_invitations (id, group_id, invited_user_id, invited_by_id, status, created_at) VALUES ($1,$2,$3,$4,$5,NOW())',
      [invId, groupId, memberId, req.userId, 'pending']
    );
    const group = await getGroupById(groupId);
    const inviterResult = await query('SELECT name FROM users WHERE id = $1', [req.userId]);
    const inviterName = inviterResult.rows[0]?.name || 'Someone';
    const { notifyUser } = require('../services/fcm');
    notifyUser(memberId, 'Group Invitation', `${inviterName} invited you to join ${group?.name || 'a group'}`,
      { type: 'group_invitation', invitationId: invId }).catch(() => {});
    return res.status(202).json({ message: 'Invitation sent — waiting for user consent' });
  }

  await query(
    'INSERT INTO group_members (id, group_id, user_id, role, joined_at) VALUES ($1,$2,$3,$4,NOW())',
    [uuidv4(), groupId, memberId, 'member']
  );
  res.json({ message: 'Member added' });

  const { notifyGroupMembers } = require('../services/fcm');
  const group = await getGroupById(groupId);
  const memberIds = (await getMembers(groupId)).map(m => m.user_id);
  notifyGroupMembers(groupId, req.userId, memberIds, 'New member', `${targetUser.name} joined ${group?.name || 'the group'}`).catch(() => {});
});

// DELETE /groups/:groupId/members/:memberId
router.delete('/:groupId/members/:memberId', async (req, res) => {
  const { groupId, memberId } = req.params;

  if (!(await isAdmin(groupId, req.userId)))
    return res.status(403).json({ message: 'Only admins can remove members' });

  if (memberId === req.userId)
    return res.status(400).json({ message: 'Use /leave to leave the group' });

  const result = await query(
    'DELETE FROM group_members WHERE group_id = $1 AND user_id = $2',
    [groupId, memberId]
  );
  if (result.rowCount) res.json({ message: 'Member removed' });
  else res.status(404).json({ message: 'Member not found' });
});

// POST /groups/:groupId/transfer-admin
router.post('/:groupId/transfer-admin', async (req, res) => {
  const { groupId } = req.params;
  const { toUserId } = req.body;

  if (!(await isAdmin(groupId, req.userId)))
    return res.status(403).json({ message: 'Only admins can transfer admin role' });

  if (!(await isMember(groupId, toUserId)))
    return res.status(400).json({ message: 'Target user is not a member' });

  await query('UPDATE group_members SET role = $1 WHERE group_id = $2 AND user_id = $3', ['member', groupId, req.userId]);
  await query('UPDATE group_members SET role = $1 WHERE group_id = $2 AND user_id = $3', ['admin', groupId, toUserId]);
  res.json({ message: 'Admin transferred successfully' });
});

// POST /groups/:groupId/leave
router.post('/:groupId/leave', async (req, res) => {
  const { groupId } = req.params;

  if (!(await isMember(groupId, req.userId)))
    return res.status(400).json({ message: 'You are not a member of this group' });

  const count = await getMemberCount(groupId);
  if (count === 1) {
    await query('UPDATE expense_groups SET is_archived = true, archived_at = NOW() WHERE id = $1', [groupId]);
    return res.json({ message: 'You were the last member. Group has been archived.' });
  }

  // If leaving admin, auto-promote next member
  if (await isAdmin(groupId, req.userId)) {
    const next = await query(
      'SELECT user_id FROM group_members WHERE group_id = $1 AND user_id != $2 LIMIT 1',
      [groupId, req.userId]
    );
    if (next.rows.length)
      await query('UPDATE group_members SET role = $1 WHERE group_id = $2 AND user_id = $3', ['admin', groupId, next.rows[0].user_id]);
  }

  await query('DELETE FROM group_members WHERE group_id = $1 AND user_id = $2', [groupId, req.userId]);
  res.json({ message: 'Left group successfully' });
});

// PATCH /groups/:groupId/archive
router.patch('/:groupId/archive', async (req, res) => {
  const { groupId } = req.params;
  if (!(await isAdmin(groupId, req.userId)))
    return res.status(403).json({ message: 'Only admins can archive the group' });

  const result = await query(
    'UPDATE expense_groups SET is_archived = true, archived_at = NOW() WHERE id = $1',
    [groupId]
  );
  if (result.rowCount) res.json({ message: 'Group archived successfully' });
  else res.status(404).json({ message: 'Group not found' });
});

// PATCH /groups/:groupId/unarchive
router.patch('/:groupId/unarchive', async (req, res) => {
  const { groupId } = req.params;
  if (!(await isAdmin(groupId, req.userId)))
    return res.status(403).json({ message: 'Only admins can unarchive the group' });

  const result = await query(
    'UPDATE expense_groups SET is_archived = false, archived_at = NULL WHERE id = $1',
    [groupId]
  );
  if (result.rowCount) res.json({ message: 'Group unarchived successfully' });
  else res.status(404).json({ message: 'Group not found' });
});

// DELETE /groups/:groupId
router.delete('/:groupId', async (req, res) => {
  const { groupId } = req.params;
  if (!(await isAdmin(groupId, req.userId)))
    return res.status(403).json({ message: 'Only admins can delete the group' });

  await query('DELETE FROM group_members WHERE group_id = $1', [groupId]);
  const result = await query('DELETE FROM expense_groups WHERE id = $1', [groupId]);
  if (result.rowCount) res.json({ message: 'Group deleted' });
  else res.status(404).json({ message: 'Group not found' });
});

// POST /groups/join/:token
router.post('/join/:token', async (req, res) => {
  const { token } = req.params;
  const groupResult = await query('SELECT * FROM expense_groups WHERE invite_token = $1', [token]);
  const group = groupResult.rows[0];
  if (!group) return res.status(404).json({ message: 'Invalid invite link' });

  const count = await getMemberCount(group.id);
  const maxMembers = group.max_members || 50;
  if (count >= maxMembers)
    return res.status(403).json({ message: `Group is full (max ${maxMembers} members)` });

  if (!(await isMember(group.id, req.userId))) {
    await query(
      'INSERT INTO group_members (id, group_id, user_id, role, joined_at) VALUES ($1,$2,$3,$4,NOW())',
      [uuidv4(), group.id, req.userId, 'member']
    );
  }

  const finalCount = await getMemberCount(group.id);
  res.json(toGroupResponse(group, finalCount, group.created_at, 0));
});

// GET /groups/:groupId/debtors — who the current user owes money to in this group
router.get('/:groupId/debtors', async (req, res) => {
  const { groupId } = req.params;
  if (!(await isMember(groupId, req.userId)))
    return res.status(403).json({ message: 'You are not a member of this group' });

  // Accepted spaces where the user is a participant but NOT the launcher
  const result = await query(
    `SELECT sp.share, s.launcher_id, u.name AS launcher_name
     FROM space_participants sp
     JOIN spaces s ON s.id = sp.space_id
     JOIN users u  ON u.id = s.launcher_id
     WHERE s.group_id = $1
       AND sp.user_id  = $2
       AND sp.user_id != s.launcher_id
       AND sp.acceptance_status = 'accepted'
       AND s.status IN ('active', 'settling')`,
    [groupId, req.userId]
  );

  // Sum shares per launcher (multiple spaces may target the same person)
  const map = {};
  for (const row of result.rows) {
    if (!map[row.launcher_id]) {
      map[row.launcher_id] = { userId: row.launcher_id, name: row.launcher_name, amount: 0 };
    }
    map[row.launcher_id].amount += parseFloat(row.share);
  }

  res.json(Object.values(map).map(d => ({ ...d, amount: Math.round(d.amount * 100) / 100 })));
});

// ── Helpers ─────────────────────────────────────────────────────────────────

async function getGroupById(groupId) {
  const r = await query('SELECT * FROM expense_groups WHERE id = $1', [groupId]);
  return r.rows[0] || null;
}

async function getMembers(groupId) {
  const r = await query(
    'SELECT m.*, u.name FROM group_members m JOIN users u ON u.id = m.user_id WHERE m.group_id = $1',
    [groupId]
  );
  return r.rows;
}

async function isMember(groupId, userId) {
  const r = await query('SELECT id FROM group_members WHERE group_id = $1 AND user_id = $2', [groupId, userId]);
  return r.rows.length > 0;
}

async function isAdmin(groupId, userId) {
  const r = await query('SELECT role FROM group_members WHERE group_id = $1 AND user_id = $2', [groupId, userId]);
  return r.rows[0]?.role === 'admin';
}

async function getMemberCount(groupId) {
  const r = await query('SELECT COUNT(*) FROM group_members WHERE group_id = $1', [groupId]);
  return parseInt(r.rows[0].count);
}

async function getMemberCounts(groupIds) {
  if (!groupIds.length) return {};
  const r = await query(
    'SELECT group_id, COUNT(*) FROM group_members WHERE group_id = ANY($1) GROUP BY group_id',
    [groupIds]
  );
  return Object.fromEntries(r.rows.map(row => [row.group_id, parseInt(row.count)]));
}

async function getLastActivities(groupIds) {
  if (!groupIds.length) return {};
  const r = await query(
    'SELECT group_id, MAX(created_at) AS last_at FROM expenses WHERE group_id = ANY($1) GROUP BY group_id',
    [groupIds]
  );
  return Object.fromEntries(r.rows.map(row => [row.group_id, row.last_at]));
}

async function getUserBalancesForGroups(groupIds, userId) {
  if (!groupIds.length) return {};
  const balances = {};

  const paid = await query(
    'SELECT group_id, SUM(amount) AS total FROM expenses WHERE group_id = ANY($1) AND paid_by = $2 GROUP BY group_id',
    [groupIds, userId]
  );
  paid.rows.forEach(r => { balances[r.group_id] = (balances[r.group_id] || 0) + parseFloat(r.total); });

  const owed = await query(
    `SELECT e.group_id, SUM(ep.share) AS total
     FROM expense_participants ep JOIN expenses e ON e.id = ep.expense_id
     WHERE e.group_id = ANY($1) AND ep.user_id = $2 GROUP BY e.group_id`,
    [groupIds, userId]
  );
  owed.rows.forEach(r => { balances[r.group_id] = (balances[r.group_id] || 0) - parseFloat(r.total); });

  return balances;
}

function toGroupResponse(g, memberCount, lastActivityAt, userBalance) {
  return {
    id: g.id,
    name: g.name,
    emoji: g.emoji,
    description: g.description,
    createdBy: g.created_by,
    isArchived: g.is_archived,
    inviteToken: g.invite_token,
    memberCount,
    lastActivityAt,
    userBalance,
  };
}

module.exports = router;
