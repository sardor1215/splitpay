const { neonConfig, Pool } = require('@neondatabase/serverless');
const ws = require('ws');

// Use WebSocket (port 443) — bypasses firewall blocks on port 5432
neonConfig.webSocketConstructor = ws;

const connectionString = `postgresql://${process.env.DB_USER}:${process.env.DB_PASSWORD}@${process.env.DB_HOST}/${process.env.DB_NAME}?sslmode=require`;

const pool = new Pool({ connectionString, max: 10 });

pool.on('error', (err) => {
  console.error('Unexpected DB client error', err);
});

module.exports = { pool, query: (text, params) => pool.query(text, params) };
