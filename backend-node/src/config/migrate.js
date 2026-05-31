const { query } = require('./db');

async function runMigrations() {
  // ── AML : nouvelles colonnes users ──────────────────────────────────────
  await query(`ALTER TABLE users ADD COLUMN IF NOT EXISTS is_pep            BOOLEAN DEFAULT FALSE`);
  await query(`ALTER TABLE users ADD COLUMN IF NOT EXISTS on_sanctions_list BOOLEAN DEFAULT FALSE`);
  await query(`ALTER TABLE users ADD COLUMN IF NOT EXISTS country_code      VARCHAR(2) DEFAULT 'FR'`);
  await query(`ALTER TABLE users ADD COLUMN IF NOT EXISTS high_risk_country BOOLEAN DEFAULT FALSE`);
  await query(`ALTER TABLE users ADD COLUMN IF NOT EXISTS edd_required      BOOLEAN DEFAULT FALSE`);
  await query(`ALTER TABLE users ADD COLUMN IF NOT EXISTS account_frozen    BOOLEAN DEFAULT FALSE`);
  await query(`ALTER TABLE users ADD COLUMN IF NOT EXISTS frozen_at         TIMESTAMPTZ`);

  // ── SAR reports ─────────────────────────────────────────────────────────
  await query(`
    CREATE TABLE IF NOT EXISTS sar_reports (
      id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      user_id     UUID REFERENCES users(id),
      rule        VARCHAR(5)  NOT NULL,
      reason      TEXT,
      amount      NUMERIC(12,2),
      expense_id  UUID,
      space_id    UUID,
      payment_id  UUID,
      status      VARCHAR(20) DEFAULT 'submitted',
      created_at  TIMESTAMPTZ DEFAULT NOW()
    )
  `);

  // ── P2P payment compliance columns ───────────────────────────────────────
  await query(`ALTER TABLE aml_alerts   ADD COLUMN IF NOT EXISTS payment_id UUID`);
  await query(`ALTER TABLE sar_reports  ADD COLUMN IF NOT EXISTS payment_id UUID`);

  // ── Espaces ──────────────────────────────────────────────────────────────
  await query(`
    CREATE TABLE IF NOT EXISTS spaces (
      id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      group_id                  UUID NOT NULL REFERENCES expense_groups(id) ON DELETE CASCADE,
      name                      VARCHAR(200) NOT NULL,
      category                  VARCHAR(50)  DEFAULT 'autre',
      total_amount              NUMERIC(12,2) NOT NULL,
      split_mode                VARCHAR(20)  DEFAULT 'equally',
      settlement_mode           VARCHAR(10)  DEFAULT 'PAY',
      due_date                  TIMESTAMPTZ,
      launcher_id               UUID REFERENCES users(id),
      status                    VARCHAR(30)  DEFAULT 'pending_acceptance',
      force_launched            BOOLEAN      DEFAULT FALSE,
      early_settle_requested    BOOLEAN      DEFAULT FALSE,
      early_settle_requested_at TIMESTAMPTZ,
      created_by                UUID REFERENCES users(id),
      created_at                TIMESTAMPTZ  DEFAULT NOW(),
      updated_at                TIMESTAMPTZ
    )
  `);

  await query(`
    CREATE TABLE IF NOT EXISTS space_participants (
      id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      space_id          UUID NOT NULL REFERENCES spaces(id) ON DELETE CASCADE,
      user_id           UUID NOT NULL REFERENCES users(id),
      share             NUMERIC(12,2),
      acceptance_status VARCHAR(20) DEFAULT 'pending',
      responded_at      TIMESTAMPTZ,
      assisted_by       UUID REFERENCES users(id),
      UNIQUE(space_id, user_id)
    )
  `);

  await query(`
    CREATE TABLE IF NOT EXISTS space_early_settle_votes (
      id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      space_id  UUID NOT NULL REFERENCES spaces(id) ON DELETE CASCADE,
      user_id   UUID NOT NULL REFERENCES users(id),
      vote      VARCHAR(10),
      voted_at  TIMESTAMPTZ DEFAULT NOW(),
      UNIQUE(space_id, user_id)
    )
  `);

  await query(`
    CREATE TABLE IF NOT EXISTS space_quorum_confirmations (
      id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      space_id         UUID NOT NULL REFERENCES spaces(id) ON DELETE CASCADE,
      settlement_cycle INT  DEFAULT 1,
      user_id          UUID NOT NULL REFERENCES users(id),
      status           VARCHAR(20) DEFAULT 'pending',
      designated_at    TIMESTAMPTZ DEFAULT NOW(),
      expires_at       TIMESTAMPTZ,
      responded_at     TIMESTAMPTZ
    )
  `);

  await query(`
    CREATE TABLE IF NOT EXISTS space_audit_log (
      id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      space_id   UUID REFERENCES spaces(id) ON DELETE CASCADE,
      user_id    UUID,
      event_type VARCHAR(60) NOT NULL,
      details    JSONB DEFAULT '{}',
      created_at TIMESTAMPTZ DEFAULT NOW()
    )
  `);

  console.log('>>> Migrations OK');
}

module.exports = { runMigrations };
