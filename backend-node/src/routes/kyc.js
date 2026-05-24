const express = require('express');
const path    = require('path');
const fs      = require('fs');
const { v4: uuidv4 } = require('uuid');
const { query }  = require('../config/db');
const { authenticate } = require('../middleware/auth');
const fcmService = require('../services/fcm');

const router = express.Router();
router.use(authenticate);

const UPLOAD_DIR   = path.join(__dirname, '../../uploads/kyc');
const ALLOWED_TYPES = ['id_front', 'id_back', 'passport', 'selfie'];

// POST /kyc/documents — upload a document (JSON base64)
// Body: { docType, fileData (base64), fileName }
router.post('/documents', async (req, res) => {
  const userId   = req.userId;
  const { docType, fileData, fileName } = req.body;

  if (!docType || !ALLOWED_TYPES.includes(docType))
    return res.status(400).json({ message: 'docType must be one of: id_front, id_back, passport, selfie' });
  if (!fileData)
    return res.status(400).json({ message: 'No file data provided (fileData base64 required)' });

  const buffer   = Buffer.from(fileData, 'base64');
  const ext      = (fileName && path.extname(fileName)) || '.jpg';
  const userDir  = path.join(UPLOAD_DIR, userId);
  fs.mkdirSync(userDir, { recursive: true });
  const filename = `${userId}_${docType}_${Date.now()}${ext}`;
  const filePath = path.join(userDir, filename);
  fs.writeFileSync(filePath, buffer);

  const existing = await query('SELECT id FROM kyc_documents WHERE user_id=$1 AND doc_type=$2', [userId, docType]);
  let docId;
  if (existing.rows.length) {
    docId = existing.rows[0].id;
    await query(
      'UPDATE kyc_documents SET file_path=$1, status=$2, rejection_reason=NULL WHERE id=$3',
      [filePath, 'uploaded', docId]
    );
  } else {
    docId = uuidv4();
    await query(
      'INSERT INTO kyc_documents (id, user_id, doc_type, file_path, status, created_at) VALUES ($1,$2,$3,$4,$5,NOW())',
      [docId, userId, docType, filePath, 'uploaded']
    );
  }

  const docResult = await query('SELECT * FROM kyc_documents WHERE id=$1', [docId]);
  const doc = docResult.rows[0];
  res.status(201).json(toDocResponse(doc));
});

// POST /kyc/submit
router.post('/submit', async (req, res) => {
  const userId = req.userId;
  const docs = await query('SELECT doc_type FROM kyc_documents WHERE user_id=$1 AND status != $2', [userId, 'rejected']);
  const types = docs.rows.map(d => d.doc_type);
  const hasAll = ALLOWED_TYPES.every(t => types.includes(t));
  if (!hasAll)
    return res.status(400).json({ message: 'All 4 documents are required: id_front, id_back, passport, selfie' });

  await query('UPDATE kyc_documents SET status=$1 WHERE user_id=$2 AND status=$3', ['pending_review', userId, 'uploaded']);
  await query('UPDATE users SET kyc_status=$1 WHERE id=$2', ['pending', userId]);
  res.json({ message: 'Documents submitted for review' });
});

// GET /kyc/status
router.get('/status', async (req, res) => {
  const [userResult, docsResult] = await Promise.all([
    query('SELECT kyc_status FROM users WHERE id=$1', [req.userId]),
    query('SELECT * FROM kyc_documents WHERE user_id=$1', [req.userId]),
  ]);
  const user = userResult.rows[0];
  if (!user) return res.status(404).json({ message: 'User not found' });
  res.json({ kycStatus: user.kyc_status, documents: docsResult.rows.map(toDocResponse) });
});

// ── Helpers ─────────────────────────────────────────────────────────────────

async function requireAdmin(req, res, next) {
  const result = await query('SELECT is_admin FROM users WHERE id=$1', [req.userId]);
  if (!result.rows[0]?.is_admin) return res.status(403).json({ message: 'Admin access required' });
  next();
}

function toDocResponse(doc) {
  return {
    id: doc.id, docType: doc.doc_type, status: doc.status,
    rejectionReason: doc.rejection_reason || null, createdAt: doc.created_at,
  };
}

module.exports = router;
