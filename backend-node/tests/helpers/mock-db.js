/**
 * Pattern-matching DB mock for AML / Spaces unit tests.
 * Call setCtx() before each test to control what each query returns.
 */

let ctx = {
  user: null,          // full user row (null = not found)
  monthlyTotal: 0,     // pre-existing monthly spend (evaluate adds the new amt)
  highFreqCount: 0,    // number of recent expenses in group (unusual pattern)
  roundAmtCount: 0,    // number of round-amount expenses (unusual pattern)
  smurfingCount: 0,    // small transactions in short window
  alertCount: 0,       // pending AML alerts (for escalation check)
};

function setCtx(overrides) {
  ctx = { ...ctx, ...overrides };
}

function resetCtx() {
  ctx = {
    user: null,
    monthlyTotal: 0,
    highFreqCount: 0,
    roundAmtCount: 0,
    smurfingCount: 0,
    alertCount: 0,
  };
}

/** Build a standard user row with sane defaults + optional overrides. */
function makeUser(overrides = {}) {
  return {
    id:                 'test-user-id',
    name:               'Test User',
    is_verified:        true,
    kyc_status:         'approved',
    is_pep:             false,
    on_sanctions_list:  false,
    high_risk_country:  false,
    edd_required:       false,
    account_frozen:     false,
    aml_status:         'clear',
    ...overrides,
  };
}

async function query(sql, _params) {
  const s = sql.toLowerCase().trim().replace(/\s+/g, ' ');

  // ── User SELECT (full row) ─────────────────────────────────────────────
  if (s.startsWith('select * from users where id')) {
    if (!ctx.user) return { rows: [], rowCount: 0 };
    return { rows: [ctx.user], rowCount: 1 };
  }

  // ── Monthly total (payments table, from_user) ─────────────────────────
  if (s.includes('coalesce(sum(amount)') && s.includes('from_user') && s.includes('payments')) {
    return { rows: [{ total: String(ctx.monthlyTotal) }], rowCount: 1 };
  }

  // ── Unusual: round-amount count (amount % 50 = 0, payments table) ─────
  if (s.includes('amount::numeric % 50 = 0') || s.includes('amount % 50 = 0')) {
    return { rows: [{ count: String(ctx.roundAmtCount) }], rowCount: 1 };
  }

  // ── Smurfing: small tx count (payments + spaces + amount <) ────────────
  if (s.includes('select count(*)') && s.includes('payments') && s.includes('spaces') && s.includes('amount <')) {
    return { rows: [{ count: String(ctx.smurfingCount) }], rowCount: 1 };
  }

  // ── Unusual: high-frequency count (payments + spaces + group_id) ───────
  if (s.includes('select count(*)') && s.includes('payments') && s.includes('spaces') && s.includes('group_id')) {
    return { rows: [{ count: String(ctx.highFreqCount) }], rowCount: 1 };
  }

  // ── AML alerts count (for escalation) ─────────────────────────────────
  if (s.includes('select count(*)') && s.includes('aml_alerts')) {
    return { rows: [{ count: String(ctx.alertCount) }], rowCount: 1 };
  }

  // ── User aml_status + name (checkEscalation) ──────────────────────────
  if (s.includes('aml_status') && s.includes('name') && s.includes('from users')) {
    return {
      rows: [{ name: ctx.user?.name || 'Test', aml_status: ctx.user?.aml_status || 'clear' }],
      rowCount: 1,
    };
  }

  // ── User name only ─────────────────────────────────────────────────────
  if (s.startsWith('select name from users')) {
    return { rows: [{ name: ctx.user?.name || 'Test' }], rowCount: 1 };
  }

  // ── All writes (INSERT / UPDATE / DELETE) ──────────────────────────────
  if (s.startsWith('insert') || s.startsWith('update') || s.startsWith('delete')) {
    return { rows: [], rowCount: 1 };
  }

  console.warn('[MOCK-DB] Unhandled query:', sql.slice(0, 100));
  return { rows: [], rowCount: 0 };
}

module.exports = { query, setCtx, resetCtx, makeUser, pool: { query } };
