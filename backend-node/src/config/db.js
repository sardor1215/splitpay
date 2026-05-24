const { Pool } = require('pg');

const pool = new Pool({
  host:     process.env.DB_HOST,
  port:     parseInt(process.env.DB_PORT || '5432'),
  database: process.env.DB_NAME,
  user:     process.env.DB_USER,
  password: process.env.DB_PASSWORD,
  max: 10,
  idleTimeoutMillis: 300_000,
  connectionTimeoutMillis: 30_000,
});

pool.on('error', (err) => {
  console.error('Unexpected DB client error', err);
});

module.exports = { pool, query: (text, params) => pool.query(text, params) };
