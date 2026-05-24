const express = require('express');
const { query } = require('../config/db');
const { authenticate } = require('../middleware/auth');
const amlService  = require('../services/aml');
const gdprService = require('../services/gdpr');
const fcmService  = require('../services/fcm');

const router = express.Router();
router.use(authenticate);
router.use(requireAdmin);

async function requireAdmin(req, res, next) {
  const result = await query('SELECT is_admin FROM users WHERE id = $1', [req.userId]);
  if (!result.rows[0]?.is_admin) return res.status(403).json({ message: 'Admin access required' });
  next();
}

// GET /admin/stats
router.get('/stats', async (req, res) => {
  const [users, groups, expenses] = await Promise.all([
    query('SELECT COUNT(*) FROM users WHERE is_deleted = false'),
    query('SELECT COUNT(*) FROM expense_groups'),
    query('SELECT COUNT(*), COALESCE(SUM(amount),0) AS total FROM expenses'),
  ]);
  res.json({
    totalUsers:    parseInt(users.rows[0].count),
    totalGroups:   parseInt(groups.rows[0].count),
    totalExpenses: parseInt(expenses.rows[0].count),
    totalAmount:   parseFloat(expenses.rows[0].total),
  });
});

// GET /admin/users
router.get('/users', async (req, res) => {
  const result = await query(
    'SELECT id, name, email, is_verified, is_admin, aml_status, kyc_status, account_balance, last_activity_at, created_at FROM users WHERE is_deleted = false ORDER BY created_at DESC'
  );
  res.json(result.rows.map(u => ({
    id: u.id, name: u.name, email: u.email, isVerified: u.is_verified, isAdmin: u.is_admin,
    amlStatus: u.aml_status, kycStatus: u.kyc_status,
    accountBalance: parseFloat(u.account_balance ?? 0),
    lastActivityAt: u.last_activity_at, createdAt: u.created_at,
  })));
});

// GET /admin/groups
router.get('/groups', async (req, res) => {
  const groups = await query('SELECT * FROM expense_groups ORDER BY created_at DESC');
  const result = await Promise.all(groups.rows.map(async g => {
    const [members, expenses] = await Promise.all([
      query('SELECT COUNT(*) FROM group_members WHERE group_id = $1', [g.id]),
      query('SELECT COUNT(*), COALESCE(SUM(amount),0) AS total FROM expenses WHERE group_id = $1', [g.id]),
    ]);
    return {
      id: g.id, name: g.name, emoji: g.emoji,
      memberCount:  parseInt(members.rows[0].count),
      expenseCount: parseInt(expenses.rows[0].count),
      totalAmount:  parseFloat(expenses.rows[0].total),
      createdAt:    g.created_at,
    };
  }));
  res.json(result);
});

// GET /admin/aml/alerts?status=pending
router.get('/aml/alerts', async (req, res) => {
  const { status } = req.query;
  const result = status
    ? await query('SELECT aa.*, u.name AS user_name FROM aml_alerts aa JOIN users u ON u.id = aa.user_id WHERE aa.status = $1 ORDER BY aa.created_at DESC', [status])
    : await query('SELECT aa.*, u.name AS user_name FROM aml_alerts aa JOIN users u ON u.id = aa.user_id ORDER BY aa.created_at DESC');

  res.json(result.rows.map(a => ({
    id: a.id, userId: a.user_id, userName: a.user_name, alertType: a.alert_type,
    description: a.description, amount: a.amount ? parseFloat(a.amount) : null,
    expenseId: a.expense_id, status: a.status, reviewedBy: a.reviewed_by,
    reviewedAt: a.reviewed_at, createdAt: a.created_at,
  })));
});

// PATCH /admin/aml/alerts/:alertId
router.patch('/aml/alerts/:alertId', async (req, res) => {
  const { alertId } = req.params;
  const { status } = req.body;
  if (!['cleared', 'suspended'].includes(status))
    return res.status(400).json({ message: "Status must be 'cleared' or 'suspended'" });

  await query(
    'UPDATE aml_alerts SET status=$1, reviewed_by=$2, reviewed_at=NOW() WHERE id=$3',
    [status, req.userId, alertId]
  );

  const alertResult = await query('SELECT user_id FROM aml_alerts WHERE id = $1', [alertId]);
  const userId = alertResult.rows[0]?.user_id;
  if (userId) {
    const pending = await query('SELECT COUNT(*) FROM aml_alerts WHERE user_id=$1 AND status=$2', [userId, 'pending']);
    const hasPending = parseInt(pending.rows[0].count) > 0;
    const newStatus = status === 'suspended' ? 'suspended' : hasPending ? 'flagged' : 'clear';
    await query('UPDATE users SET aml_status=$1 WHERE id=$2', [newStatus, userId]);
  }

  res.json({ message: 'Alert updated' });
});

// POST /admin/users/:userId/lift-suspension
router.post('/users/:userId/lift-suspension', async (req, res) => {
  const { userId: targetId } = req.params;
  await query(
    'UPDATE aml_alerts SET status=$1, reviewed_by=$2, reviewed_at=NOW() WHERE user_id=$3 AND status=$4',
    ['cleared', req.userId, targetId, 'pending']
  );
  await query('UPDATE users SET aml_status=$1 WHERE id=$2', ['clear', targetId]);
  fcmService.notifyUser(targetId, 'Account Restored', 'Your account suspension has been lifted. You can resume using SplitPay.').catch(() => {});
  res.json({ message: 'Suspension lifted' });
});

// PATCH /admin/users/:userId/aml-status
router.patch('/users/:userId/aml-status', async (req, res) => {
  const { userId: targetId } = req.params;
  const { status } = req.body;
  await query('UPDATE users SET aml_status=$1 WHERE id=$2', [status, targetId]);
  res.json({ message: 'User AML status updated' });
});

// PATCH /admin/users/:userId/balance
router.patch('/users/:userId/balance', async (req, res) => {
  const { userId: targetId } = req.params;
  const { mode, amount, note } = req.body;

  if (!['set', 'add', 'subtract'].includes(mode))
    return res.status(400).json({ message: "Mode must be 'set', 'add' or 'subtract'" });
  if (typeof amount !== 'number' || amount < 0)
    return res.status(400).json({ message: 'Amount must be a positive number' });

  const userResult = await query('SELECT account_balance FROM users WHERE id=$1 AND is_deleted=false', [targetId]);
  if (!userResult.rows[0]) return res.status(404).json({ message: 'User not found' });

  const current = parseFloat(userResult.rows[0].account_balance ?? 0);
  let newBalance;
  if (mode === 'set')      newBalance = amount;
  else if (mode === 'add') newBalance = current + amount;
  else                     newBalance = Math.max(0, current - amount);

  await query('UPDATE users SET account_balance=$1 WHERE id=$2', [newBalance, targetId]);
  res.json({ message: 'Balance updated', newBalance });
});

// GET /admin/gdpr/config
router.get('/gdpr/config', async (req, res) => {
  const config = await gdprService.getConfig();
  res.json({ config });
});

// PUT /admin/gdpr/config
router.put('/gdpr/config', async (req, res) => {
  const {
    archiveAfterMonths, deleteAfterMonths, largeTransactionThreshold,
    highFrequencyCount, highFrequencyWindowHours, newAccountDays, autoSuspendAfterAlerts,
  } = req.body;

  const updates = {
    archive_after_months:        archiveAfterMonths,
    delete_after_months:         deleteAfterMonths,
    large_transaction_threshold: largeTransactionThreshold,
    high_frequency_count:        highFrequencyCount,
    high_frequency_window_hours: highFrequencyWindowHours,
    new_account_days:            newAccountDays,
    auto_suspend_after_alerts:   autoSuspendAfterAlerts,
  };

  for (const [key, value] of Object.entries(updates)) {
    if (value !== undefined) await gdprService.setConfig(key, String(value));
  }
  await gdprService.reloadConfig();
  res.json({ message: 'Configuration updated' });
});

// POST /admin/gdpr/run-cleanup
router.post('/gdpr/run-cleanup', async (req, res) => {
  try {
    await gdprService.runDailyCleanup();
    res.json({ message: 'Cleanup completed' });
  } catch (e) {
    res.status(500).json({ message: `Cleanup failed: ${e.message}` });
  }
});

// ── KYC Admin (/admin/kyc/*) ─────────────────────────────────────────────────
const path = require('path');
const fs   = require('fs');

// GET /admin/kyc/pending
router.get('/kyc/pending', async (req, res) => {
  // Show any unresolved document (not approved/rejected) belonging to a user
  // whose kyc_status is 'pending'. This covers 'pending_review' and any legacy
  // status values that may exist from older backend versions.
  const docs = await query(
    `SELECT kd.*, u.name AS user_name, u.email AS user_email
     FROM kyc_documents kd JOIN users u ON u.id = kd.user_id
     WHERE kd.status NOT IN ('approved', 'rejected')
       AND u.kyc_status = 'pending'
     ORDER BY kd.created_at ASC`
  );
  res.json(docs.rows.map(d => ({
    id: d.id, userId: d.user_id, userName: d.user_name, userEmail: d.user_email,
    docType: d.doc_type, status: d.status,
    fileUrl: `/admin/kyc/documents/${d.id}/file`,
    createdAt: d.created_at,
  })));
});

// POST /admin/kyc/users/:userId/approve-all
router.post('/kyc/users/:userId/approve-all', async (req, res) => {
  const { userId: targetId } = req.params;
  const result = await query(
    'UPDATE kyc_documents SET status=$1, reviewed_by=$2, reviewed_at=NOW() WHERE user_id=$3 AND status=$4',
    ['approved', req.userId, targetId, 'pending_review']
  );
  if (!result.rowCount) return res.status(404).json({ message: 'No pending documents found for this user' });
  await query('UPDATE users SET kyc_status=$1 WHERE id=$2', ['approved', targetId]);
  fcmService.notifyUser(targetId, 'Identity Verified', 'Your KYC has been approved. All features are now unlocked.').catch(() => {});
  res.json({ message: 'All documents approved — user is now verified' });
});

// PATCH /admin/kyc/documents/:docId
router.patch('/kyc/documents/:docId', async (req, res) => {
  const { docId } = req.params;
  const { status, rejectionReason } = req.body;

  if (!['approved', 'rejected'].includes(status))
    return res.status(400).json({ message: "Status must be 'approved' or 'rejected'" });
  if (status === 'rejected' && !rejectionReason?.trim())
    return res.status(400).json({ message: 'Rejection reason is required' });

  const docResult = await query('SELECT user_id FROM kyc_documents WHERE id=$1', [docId]);
  const doc = docResult.rows[0];
  if (!doc) return res.status(404).json({ message: 'Document not found' });

  await query(
    'UPDATE kyc_documents SET status=$1, rejection_reason=$2, reviewed_by=$3, reviewed_at=NOW() WHERE id=$4',
    [status, rejectionReason || null, req.userId, docId]
  );

  const msg = status === 'approved'
    ? 'Your KYC has been approved. All features are now unlocked.'
    : `One of your KYC documents was rejected: ${rejectionReason}. Please re-upload.`;
  fcmService.notifyUser(doc.user_id, 'KYC Update', msg).catch(() => {});
  res.json({ message: `Document ${status}` });
});

// GET /admin/kyc/documents/:docId/file
router.get('/kyc/documents/:docId/file', async (req, res) => {
  const { docId } = req.params;
  const docResult = await query('SELECT file_path FROM kyc_documents WHERE id=$1', [docId]);
  const doc = docResult.rows[0];
  if (!doc) return res.status(404).json({ message: 'Document not found' });
  if (!fs.existsSync(doc.file_path)) return res.status(404).json({ message: 'File not found on server' });
  res.sendFile(path.resolve(doc.file_path));
});

module.exports = router;
