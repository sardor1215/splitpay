const express = require('express');
const { v4: uuidv4 } = require('uuid');
const { query } = require('../config/db');
const { authenticate } = require('../middleware/auth');
const { notifyGroupMembers, notifyUser } = require('../services/fcm');

const router = express.Router();
router.use(authenticate);

// GET /invitations/pending  (group invitations only — expenses removed)
router.get('/pending', async (req, res) => {
  const groupInvs = await query(
    `SELECT gi.id, gi.created_at, g.name AS group_name, g.emoji AS group_emoji,
            u.name AS invited_by_name
     FROM group_invitations gi
     JOIN expense_groups g ON g.id = gi.group_id
     JOIN users u ON u.id = gi.invited_by_id
     WHERE gi.invited_user_id = $1 AND gi.status = 'pending'`,
    [req.userId]
  );

  res.json(groupInvs.rows.map(i => ({
    id: i.id, type: 'group', title: i.group_name,
    subtitle: `Invited by ${i.invited_by_name}`,
    emoji: i.group_emoji, amount: null,
    invitedByName: i.invited_by_name, createdAt: i.created_at,
  })));
});

// POST /invitations/:id/accept  (group invitation)
router.post('/:id/accept', async (req, res) => {
  const { id } = req.params;
  const result = await query(
    `SELECT gi.*, g.name AS group_name FROM group_invitations gi
     JOIN expense_groups g ON g.id = gi.group_id WHERE gi.id = $1`,
    [id]
  );
  const inv = result.rows[0];
  if (!inv) return res.status(404).json({ message: 'Invitation not found' });
  if (inv.invited_user_id !== req.userId) return res.status(403).json({ message: 'Not your invitation' });
  if (inv.status !== 'pending') return res.status(409).json({ message: `Invitation already ${inv.status}` });

  await query(
    'INSERT INTO group_members (id, group_id, user_id, role, joined_at) VALUES ($1,$2,$3,$4,NOW())',
    [uuidv4(), inv.group_id, req.userId, 'member']
  );
  await query('UPDATE group_invitations SET status=$1 WHERE id=$2', ['accepted', id]);
  res.json({ message: `You joined ${inv.group_name}` });

  const [membersResult, userResult] = await Promise.all([
    query('SELECT user_id FROM group_members WHERE group_id=$1', [inv.group_id]),
    query('SELECT name FROM users WHERE id=$1', [req.userId]),
  ]);
  const memberIds = membersResult.rows.map(m => m.user_id);
  const userName  = userResult.rows[0]?.name || 'A new member';

  // Broadcast to the whole group
  notifyGroupMembers(inv.group_id, req.userId, memberIds, 'New member', `${userName} joined ${inv.group_name}`).catch(() => {});

  // Direct notification to the inviter so they know their invitation was accepted
  notifyUser(
    inv.invited_by_id,
    'Invitation accepted',
    `${userName} accepted your invitation and joined "${inv.group_name}".`,
    { type: 'group_invitation_accepted', groupId: inv.group_id }
  ).catch(() => {});
});

// POST /invitations/:id/decline  (group invitation)
router.post('/:id/decline', async (req, res) => {
  const { id } = req.params;
  const result = await query(
    `SELECT gi.*, g.name AS group_name FROM group_invitations gi
     JOIN expense_groups g ON g.id = gi.group_id WHERE gi.id = $1`,
    [id]
  );
  const inv = result.rows[0];
  if (!inv) return res.status(404).json({ message: 'Invitation not found' });
  if (inv.invited_user_id !== req.userId) return res.status(403).json({ message: 'Not your invitation' });

  await query('UPDATE group_invitations SET status=$1 WHERE id=$2', ['declined', id]);
  res.json({ message: 'Invitation declined' });

  const declinerResult = await query('SELECT name FROM users WHERE id=$1', [req.userId]);
  const declinerName   = declinerResult.rows[0]?.name || 'Someone';

  // Notify the inviter that their invitation was declined
  notifyUser(
    inv.invited_by_id,
    'Invitation declined',
    `${declinerName} declined your invitation to join "${inv.group_name}".`,
    { type: 'group_invitation_declined', groupId: inv.group_id }
  ).catch(() => {});
});


module.exports = router;
