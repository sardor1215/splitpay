require('dotenv').config();
const app  = require('./src/app');
const { pool } = require('./src/config/db');
const { reloadConfig } = require('./src/services/gdpr');
const { runMigrations } = require('./src/config/migrate');

const PORT = process.env.PORT || 3000;

async function start() {
  await pool.query('SELECT 1');
  console.log('>>> Database connected');

  await runMigrations();

  await reloadConfig().catch(() => console.warn('Could not load GDPR config — using defaults'));

  app.listen(PORT, () => {
    console.log(`>>> Server running on port ${PORT}`);
  });
}

start().catch(err => {
  console.error('Failed to start server:', err);
  process.exit(1);
});
