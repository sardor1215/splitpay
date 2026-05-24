const { query } = require('../config/db');

async function exportUserDataJson(userId) {
  const [user, expenses, groups] = await Promise.all([
    query('SELECT id, name, email, phone, preferred_currency, created_at FROM users WHERE id = $1', [userId]),
    query(`SELECT e.title, e.amount, e.created_at, ep.share
           FROM expense_participants ep JOIN expenses e ON e.id = ep.expense_id
           WHERE ep.user_id = $1 ORDER BY e.created_at DESC`, [userId]),
    query(`SELECT g.name, g.emoji, m.role, m.joined_at
           FROM group_members m JOIN expense_groups g ON g.id = m.group_id
           WHERE m.user_id = $1`, [userId]),
  ]);

  const data = {
    exportedAt: new Date().toISOString(),
    profile:    user.rows[0] || {},
    expenses:   expenses.rows,
    groups:     groups.rows,
  };

  return JSON.stringify(data, null, 2);
}

async function exportUserDataText(userId) {
  const json = await exportUserDataJson(userId);
  const data  = JSON.parse(json);
  const lines = [
    `SplitPay Data Export — ${data.exportedAt}`,
    '='.repeat(50),
    '',
    'PROFILE',
    `  Name:     ${data.profile.name}`,
    `  Email:    ${data.profile.email}`,
    `  Phone:    ${data.profile.phone || 'N/A'}`,
    `  Currency: ${data.profile.preferred_currency}`,
    `  Joined:   ${data.profile.created_at}`,
    '',
    `GROUPS (${data.groups.length})`,
    ...data.groups.map(g => `  ${g.emoji} ${g.name} — ${g.role} since ${g.joined_at}`),
    '',
    `EXPENSES (${data.expenses.length})`,
    ...data.expenses.map(e => `  ${e.created_at?.slice(0, 10)} | ${e.title} | Total: ${e.amount} | Your share: ${e.share}`),
  ];

  return lines.join('\n');
}

module.exports = { exportUserDataJson, exportUserDataText };
