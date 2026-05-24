require('dotenv').config();
const app  = require('./src/app');
const { pool } = require('./src/config/db');
const { reloadConfig } = require('./src/services/gdpr');
const { runMigrations } = require('./src/config/migrate');

const PORT = process.env.PORT || 3000;

async function start() {
  // Verify DB connection
  await pool.query('SELECT 1');
  console.log('>>> Database connected!');

  // Run migrations (idempotent)
  await runMigrations();

  // Load GDPR/AML config from DB
  await reloadConfig().catch(() => console.warn('Could not load GDPR config — using defaults'));

  const server = app.listen(PORT, () => {
    console.log(`>>> Server running on port ${PORT}`);
  });

  server.on('error', (err) => {
    if (err.code === 'EADDRINUSE') {
      console.warn(`Port ${PORT} occupé — kill et retry...`);
      require('child_process').execSync(`lsof -ti :${PORT} | xargs kill -9 2>/dev/null || true`);
      setTimeout(() => server.listen(PORT), 500);
    } else {
      console.error('Erreur serveur:', err);
      process.exit(1);
    }
  });
}

start().catch(err => {
  console.error('Failed to start server:', err);
  process.exit(1);
});
