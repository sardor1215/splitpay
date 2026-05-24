require('dotenv').config();
const express = require('express');

const authRoutes    = require('./routes/auth');
const userRoutes    = require('./routes/users');
const groupRoutes   = require('./routes/groups');
const adminRoutes   = require('./routes/admin');
const kycRoutes     = require('./routes/kyc');
const spacesRoutes       = require('./routes/spaces');
const invitationsRoutes  = require('./routes/invitations');

const app = express();

app.use(express.json({ limit: '15mb' }));
app.use(express.urlencoded({ extended: true, limit: '15mb' }));

// Request logger
app.use((req, res, next) => {
  const start = Date.now();
  res.on('finish', () => {
    const ms      = Date.now() - start;
    const status  = res.statusCode;
    const color   = status >= 500 ? '\x1b[31m' : status >= 400 ? '\x1b[33m' : '\x1b[32m';
    const reset   = '\x1b[0m';
    const time    = new Date().toISOString().slice(11, 19);
    console.log(`${color}[${time}] ${req.method} ${req.originalUrl} ${status} — ${ms}ms${reset}`);
  });
  next();
});

// KYC uploads utilisent base64 JSON — pas besoin de multer

// Health check (public — must be before protected routes)
app.get('/health', (req, res) => res.json({ status: 'ok', timestamp: new Date().toISOString() }));

// Debug auth — teste si le token envoyé est valide
app.get('/auth-check', require('./middleware/auth').authenticate, (req, res) =>
  res.json({ ok: true, userId: req.userId }));

// Routes
app.use('/auth',  authRoutes);   // /auth/register, /auth/login, ...
app.use('/',      userRoutes);   // /me, /users/lookup, /users/fcm-token, /me/export/*
app.use('/groups', groupRoutes); // /groups, /groups/:id, members, archive, delete
app.use('/admin', adminRoutes);  // /admin/stats, /admin/users, /admin/aml/*
app.use('/kyc',   kycRoutes);    // /kyc/documents, /kyc/submit, /kyc/status, /admin/kyc/*
app.use('/invitations', invitationsRoutes); // /invitations/pending, group accept/decline
app.use('/groups/:groupId/spaces', spacesRoutes); // /groups/:id/spaces — Espaces v1.3
app.use('/spaces', spacesRoutes);                 // /spaces/pending

// Global error handler — catches both sync throws and async errors forwarded via next(err)
app.use((err, req, res, next) => {
  // PostgreSQL invalid UUID / bad syntax → 400, not 500
  if (err.code === '22P02') {
    return res.status(400).json({ message: 'Invalid ID format in request' });
  }
  console.error('[ERROR]', err.message || err);
  res.status(500).json({ message: 'Internal server error' });
});

module.exports = app;
