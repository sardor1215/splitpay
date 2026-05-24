/**
 * Moteur AML — Table de Décision SplitEase v2.0
 * AMLD6 (EU) 2018/1673 | FATF Recommendations | PSD2
 * Règles R1–R18
 */
const { query } = require('../config/db');
const { v4: uuidv4 } = require('uuid');

// ── Seuils configurables (modifiables via GDPR config) ───────────────────────
const config = {
  sddThreshold:              150.00,   // €/mois — seuil SDD (R1/R5)
  cddThreshold:           10_000.00,   // €/mois — seuil EDD/CTR (R2)
  highFrequencyCount:            10,   // transactions dans la fenêtre
  highFrequencyWindowHours:      24,
  smurfingWindowHours:            2,   // fenêtre courte pour détecter le smurfing
  smurfingMinTransactions:        5,   // min transactions petites sur la fenêtre
  newAccountDays:                30,
  autoSuspendAfterAlerts:         3,
};

// ── Entrée publique — pré-vérification AVANT création d'une dépense/espace ──
async function preCheck(userId, amount, groupId, spaceId = null) {
  const result = await evaluate(userId, parseFloat(amount), groupId, spaceId);

  if (result.action === 'BLOCK' || result.action === 'FREEZE') {
    await applyActions(result, userId, amount, null, spaceId);
    return result.reason;   // message retourné → bloque la transaction
  }
  if (result.action === 'ALERT') {
    applyActions(result, userId, amount, null, spaceId).catch(() => {});
  }
  return null;  // null = autorisé
}

// ── Vérification post-création (monitoring asynchrone) ───────────────────────
async function checkExpense(expenseId, userId, amount, groupId) {
  const result = await evaluate(userId, parseFloat(amount), groupId, null);
  await applyActions(result, userId, amount, expenseId, null);
}

// ── Moteur de règles R1–R18 ──────────────────────────────────────────────────
async function evaluate(userId, amt, groupId, spaceId) {
  const user = (await query('SELECT * FROM users WHERE id = $1', [userId])).rows[0];
  if (!user) return rule('R18', 'BLOCK', 'Account not found');

  // Conditions profil utilisateur (A1–A5)
  const A1_verified       = user.is_verified === true;
  const A2_kycCompleted   = user.kyc_status === 'approved';
  const A3_isPEP          = user.is_pep === true;
  const A4_onSanctions    = user.on_sanctions_list === true;
  const A5_highRiskCountry= user.high_risk_country === true;

  // Conditions transaction (B1–B6)
  const monthlyTotal = await getMonthlyTotal(userId) + amt;
  const B1_belowSDD   = monthlyTotal < config.sddThreshold;
  const B2_cddRange   = monthlyTotal >= config.sddThreshold && monthlyTotal <= config.cddThreshold;
  const B3_aboveEDD   = amt > config.cddThreshold;
  const B4_unusual    = await detectUnusualPattern(userId, amt, groupId);
  const B5_highRiskTx = A5_highRiskCountry;
  const B6_smurfing   = await detectSmurfing(userId, groupId);

  // ── Évaluation des 18 règles dans l'ordre de priorité ────────────────────

  // R18 — Compte non vérifié → BLOQ systématique
  if (!A1_verified)
    return rule('R18', 'BLOCK', 'Unverified account — transaction systematically blocked');

  // R17 — Sanctions + compte non vérifié KYC
  if (A4_onSanctions && !A2_kycCompleted)
    return rule('R17', 'BLOCK', 'User on sanctions list (account not KYC verified)', { freeze: true, sar: true });

  // R4 — Sanctions (compte vérifié)
  if (A4_onSanctions && !A5_highRiskCountry)
    return rule('R4', 'BLOCK', 'User on sanctions list (OFAC/EU)', { freeze: true, sar: true, notify72h: true });

  // R8 — Sanctions + pays risque élevé
  if (A4_onSanctions && A5_highRiskCountry)
    return rule('R8', 'BLOCK', 'Sanctions + high-risk country', { freeze: true, sar: true, notify72h: true });

  // R12 — PEP + smurfing + unusual pattern → CRITICAL
  if (A3_isPEP && B6_smurfing && B4_unusual)
    return rule('R12', 'BLOCK', 'PEP + smurfing + unusual pattern — maximum risk', { freeze: true, sar: true, edd: true });

  // R16 — All high-risk conditions met
  if (B4_unusual && !A2_kycCompleted && A5_highRiskCountry)
    return rule('R16', 'BLOCK', 'Convergence of high-risk factors', { freeze: true, sar: true, notify72h: true });

  // R11 — Smurfing + unusual pattern
  if (B6_smurfing && B4_unusual)
    return rule('R11', 'BLOCK', 'Smurfing detected + unusual pattern', { sar: true });

  // R15 — Suspicious pattern + EDD required
  if (B4_unusual && !A2_kycCompleted)
    return rule('R15', 'BLOCK', 'Suspicious pattern + EDD required — manual review needed');

  // R14 — KYC incomplete + CDD range + EDD
  if (!A2_kycCompleted && B2_cddRange && B4_unusual)
    return rule('R14', 'BLOCK', 'KYC incomplete + CDD amount + EDD required', { freeze: true, sar: true });

  // R13 — KYC incomplete + <150€ + EDD
  if (!A2_kycCompleted && B1_belowSDD && B4_unusual)
    return rule('R13', 'BLOCK', 'KYC incomplete + EDD required');

  // R6 — KYC incomplete + amount above SDD threshold
  if (!A2_kycCompleted && B2_cddRange)
    return rule('R6', 'BLOCK', 'KYC incomplete — CDD required for this amount. Transaction suspended.');

  // R3 — PEP detected (KYC CDD approved) → EDD mandatory
  if (A3_isPEP && A2_kycCompleted)
    return rule('R3', 'EDD', 'PEP status detected — Enhanced Due Diligence required');

  // R10 — CDD approved + unusual pattern + high amount
  if (A2_kycCompleted && B4_unusual && B2_cddRange)
    return rule('R10', 'ALERT', 'High amount + unusual pattern — enhanced CDD review', { enhanced: true });

  // R9 — Unusual transaction pattern (regardless of KYC level)
  if (B4_unusual)
    return rule('R9', 'ALERT', 'Unusual transaction pattern detected', { monitoring30d: true });

  // R7 — KYC approved + high-risk country
  if (A2_kycCompleted && A5_highRiskCountry)
    return rule('R7', 'ALERT', 'High-risk country — enhanced CDD + source of funds verification', { enhanced: true });

  // R8 (safety check)
  if (B5_highRiskTx && A4_onSanctions)
    return rule('R8', 'BLOCK', 'Sanctions + high-risk country', { freeze: true, sar: true, notify72h: true });

  // R5 — KYC incomplete + <150€
  if (!A2_kycCompleted && B1_belowSDD)
    return rule('R5', 'ALLOW', 'SDD — KYC incomplete but amount below threshold. Active monitoring.');

  // R2 — Verified + CDD range
  if (A2_kycCompleted && B2_cddRange)
    return rule('R2', 'ALLOW', 'Standard CDD — amount in the 150€–10k€ range');

  // R1 — Verified + low risk + <150€
  if (A2_kycCompleted && B1_belowSDD)
    return rule('R1', 'ALLOW', 'SDD — verified low-risk profile');

  // Amount > 10k€ → mandatory CTR filing
  if (B3_aboveEDD)
    return rule('EDD', 'BLOCK', `Amount of ${amt.toFixed(2)}€ exceeds EDD threshold (${config.cddThreshold}€) — CTR required`);

  return rule('R1', 'ALLOW', 'Standard monitoring');
}

// ── Application des actions AML ──────────────────────────────────────────────
async function applyActions(result, userId, amount, expenseId, spaceId) {
  const { ruleId, action, flags = {} } = result;

  // Créer une alerte AML
  await createAlert(userId, ruleId, result.reason, amount, expenseId, spaceId, action);

  // Geler le compte
  if (flags.freeze) {
    await query('UPDATE users SET account_frozen = true, frozen_at = NOW(), aml_status = $1 WHERE id = $2',
      ['suspended', userId]);
    const { notifyAdmins } = require('./fcm');
    const u = (await query('SELECT name FROM users WHERE id = $1', [userId])).rows[0];
    notifyAdmins('Account frozen — AML',
      `${u?.name || userId} frozen by rule ${ruleId}: ${result.reason}`,
      { userId, type: 'aml_freeze', rule: ruleId }).catch(() => {});
  }

  // Soumettre un SAR
  if (flags.sar) {
    await query(
      'INSERT INTO sar_reports (user_id, rule, reason, amount, expense_id, space_id) VALUES ($1,$2,$3,$4,$5,$6)',
      [userId, ruleId, result.reason, amount, expenseId || null, spaceId || null]
    );
    console.warn(`[AML] SAR submitted — Rule ${ruleId} — User ${userId}`);
  }

  // Escalade auto-suspension
  await checkEscalation(userId);
}

// ── Détection schéma inhabituel (basé sur les paiements réels) ───────────────
async function detectUnusualPattern(userId, amt, groupId) {
  const windowStart = new Date(Date.now() - config.highFrequencyWindowHours * 3_600_000);

  // Vélocité anormale sur les paiements
  const freq = await query(
    `SELECT COUNT(*) FROM payments p
     JOIN spaces s ON s.id = p.space_id
     WHERE p.from_user = $1 AND s.group_id = $2 AND p.paid_at >= $3`,
    [userId, groupId, windowStart]
  );
  if (parseInt(freq.rows[0].count) >= config.highFrequencyCount) return true;

  // Montants ronds suspects
  if (amt >= 100 && amt % 50 === 0) {
    const roundCount = await query(
      `SELECT COUNT(*) FROM payments WHERE from_user = $1 AND paid_at >= $2
       AND (amount::numeric % 50 = 0) AND amount >= 100`,
      [userId, windowStart]
    );
    if (parseInt(roundCount.rows[0].count) >= 3) return true;
  }

  return false;
}

// ── Détection smurfing (basé sur les paiements réels) ────────────────────────
async function detectSmurfing(userId, groupId) {
  const windowStart = new Date(Date.now() - config.smurfingWindowHours * 3_600_000);
  const res = await query(
    `SELECT COUNT(*) FROM payments p
     JOIN spaces s ON s.id = p.space_id
     WHERE p.from_user = $1 AND s.group_id = $2 AND p.paid_at >= $3 AND p.amount < $4`,
    [userId, groupId, windowStart, config.sddThreshold]
  );
  return parseInt(res.rows[0].count) >= config.smurfingMinTransactions;
}

// ── Total mensuel d'un utilisateur (basé sur les paiements réels) ────────────
async function getMonthlyTotal(userId) {
  const monthStart = new Date();
  monthStart.setDate(1); monthStart.setHours(0, 0, 0, 0);
  const res = await query(
    'SELECT COALESCE(SUM(amount),0) AS total FROM payments WHERE from_user = $1 AND paid_at >= $2',
    [userId, monthStart]
  );
  return parseFloat(res.rows[0].total);
}

// ── Escalade auto-suspension ──────────────────────────────────────────────────
async function checkEscalation(userId) {
  const pending = await query(
    'SELECT COUNT(*) FROM aml_alerts WHERE user_id = $1 AND status = $2',
    [userId, 'pending']
  );
  if (parseInt(pending.rows[0].count) < config.autoSuspendAfterAlerts) return;

  const user = (await query('SELECT aml_status, name FROM users WHERE id = $1', [userId])).rows[0];
  if (user?.aml_status === 'suspended') return;

  await query('UPDATE users SET aml_status = $1, account_frozen = true, frozen_at = NOW() WHERE id = $2',
    ['suspended', userId]);

  const { notifyAdmins } = require('./fcm');
  notifyAdmins('AML Auto-suspension',
    `${user?.name || userId} automatically suspended after ${config.autoSuspendAfterAlerts} AML alerts`,
    { userId, type: 'aml_auto_suspend' }).catch(() => {});
}

// ── Créer alerte AML ──────────────────────────────────────────────────────────
async function createAlert(userId, alertType, description, amount, expenseId, spaceId, action) {
  await query(
    'INSERT INTO aml_alerts (id, user_id, alert_type, description, amount, expense_id, status, created_at) VALUES ($1,$2,$3,$4,$5,$6,$7,NOW())',
    [uuidv4(), userId, alertType, description, amount, expenseId || null, 'pending']
  );
  if (action !== 'ALLOW' && action !== 'SDD') {
    await query("UPDATE users SET aml_status = 'flagged' WHERE id = $1 AND aml_status = 'clear'", [userId]);
  }
}

// ── Helpers ───────────────────────────────────────────────────────────────────
function rule(ruleId, action, reason, flags = {}) {
  return { ruleId, action, reason, flags };
}

function updateConfig(newCfg) {
  Object.assign(config, newCfg);
}

module.exports = { preCheck, checkExpense, createAlert, updateConfig, config };
