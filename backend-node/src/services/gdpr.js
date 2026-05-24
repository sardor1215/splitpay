const { query } = require('../config/db');

async function getConfig() {
  const result = await query('SELECT key, value FROM gdpr_config');
  return Object.fromEntries(result.rows.map(r => [r.key, r.value]));
}

async function setConfig(key, value) {
  await query(
    'INSERT INTO gdpr_config (key, value) VALUES ($1,$2) ON CONFLICT (key) DO UPDATE SET value = $2',
    [key, value]
  );
}

async function reloadConfig() {
  const cfg = await getConfig();
  const { updateConfig } = require('./aml');
  updateConfig({
    largeTransactionThreshold: parseFloat(cfg.large_transaction_threshold || '1000'),
    highFrequencyCount:        parseInt(cfg.high_frequency_count || '10'),
    highFrequencyWindowHours:  parseInt(cfg.high_frequency_window_hours || '24'),
    newAccountDays:            parseInt(cfg.new_account_days || '30'),
    autoSuspendAfterAlerts:    parseInt(cfg.auto_suspend_after_alerts || '3'),
  });
}

async function runDailyCleanup() {
  const cfg = await getConfig();
  const deleteAfterMonths = parseInt(cfg.delete_after_months || '24');

  const cutoff = new Date();
  cutoff.setMonth(cutoff.getMonth() - deleteAfterMonths);

  const result = await query(
    `UPDATE users SET
      is_deleted = true, deleted_at = NOW(),
      email = CONCAT('deleted_', id, '@splitpay.invalid'),
      name = 'Deleted User', phone = NULL, avatar_url = NULL,
      password_hash = NULL, refresh_token = NULL, google_id = NULL
     WHERE is_deleted = false AND deleted_at IS NULL AND created_at < $1`,
    [cutoff]
  );
  console.log(`GDPR cleanup: anonymized ${result.rowCount} old accounts`);
}

module.exports = { getConfig, setConfig, reloadConfig, runDailyCleanup };
