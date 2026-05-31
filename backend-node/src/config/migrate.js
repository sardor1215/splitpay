const { query } = require('./db');

async function runMigrations() {

  // ── Initial schema (CREATE IF NOT EXISTS — safe to run on fresh DB) ─────────

  await query(`
    CREATE TABLE IF NOT EXISTS users (
      id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      name                      VARCHAR(200) NOT NULL,
      email                     VARCHAR(255) UNIQUE NOT NULL,
      phone                     VARCHAR(30),
      password_hash             VARCHAR(255),
      is_verified               BOOLEAN DEFAULT FALSE,
      verification_token        VARCHAR(255),
      password_reset_token      VARCHAR(255),
      password_reset_expires_at TIMESTAMPTZ,
      refresh_token             VARCHAR(512),
      google_id                 VARCHAR(255),
      avatar_url                TEXT,
      preferred_currency        VARCHAR(10) DEFAULT 'USD',
      is_deleted                BOOLEAN DEFAULT FALSE,
      deleted_at                TIMESTAMPTZ,
      is_admin                  BOOLEAN DEFAULT FALSE,
      last_activity_at          TIMESTAMPTZ,
      aml_status                VARCHAR(20) DEFAULT 'clear',
      kyc_status                VARCHAR(20) DEFAULT 'none',
      account_balance           NUMERIC(12,2) DEFAULT 0,
      require_consent           BOOLEAN DEFAULT FALSE,
      account_frozen            BOOLEAN DEFAULT FALSE,
      frozen_at                 TIMESTAMPTZ,
      is_pep                    BOOLEAN DEFAULT FALSE,
      on_sanctions_list         BOOLEAN DEFAULT FALSE,
      country_code              VARCHAR(2) DEFAULT 'FR',
      high_risk_country         BOOLEAN DEFAULT FALSE,
      edd_required              BOOLEAN DEFAULT FALSE,
      created_at                TIMESTAMPTZ DEFAULT NOW()
    )
  `);

  await query(`
    CREATE TABLE IF NOT EXISTS expense_groups (
      id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      name         VARCHAR(200) NOT NULL,
      emoji        VARCHAR(10) DEFAULT '💰',
      description  TEXT,
      created_by   UUID REFERENCES users(id),
      invite_token VARCHAR(255),
      is_archived  BOOLEAN DEFAULT FALSE,
      created_at   TIMESTAMPTZ DEFAULT NOW()
    )
  `);

  await query(`
    CREATE TABLE IF NOT EXISTS group_members (
      id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      group_id  UUID NOT NULL REFERENCES expense_groups(id) ON DELETE CASCADE,
      user_id   UUID NOT NULL REFERENCES users(id),
      role      VARCHAR(20) DEFAULT 'member',
      joined_at TIMESTAMPTZ DEFAULT NOW(),
      UNIQUE(group_id, user_id)
    )
  `);

  await query(`
    CREATE TABLE IF NOT EXISTS expenses (
      id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      group_id   UUID NOT NULL REFERENCES expense_groups(id) ON DELETE CASCADE,
      title      VARCHAR(255) NOT NULL,
      amount     NUMERIC(12,2) NOT NULL,
      paid_by    UUID REFERENCES users(id),
      split_mode VARCHAR(20) DEFAULT 'equally',
      category   VARCHAR(50) DEFAULT 'other',
      created_at TIMESTAMPTZ DEFAULT NOW()
    )
  `);

  await query(`
    CREATE TABLE IF NOT EXISTS expense_participants (
      id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      expense_id UUID NOT NULL REFERENCES expenses(id) ON DELETE CASCADE,
      user_id    UUID NOT NULL REFERENCES users(id),
      share      NUMERIC(12,2),
      UNIQUE(expense_id, user_id)
    )
  `);

  await query(`
    CREATE TABLE IF NOT EXISTS expense_activity (
      id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      expense_id  UUID REFERENCES expenses(id) ON DELETE CASCADE,
      user_id     UUID REFERENCES users(id),
      action      VARCHAR(50),
      description TEXT,
      created_at  TIMESTAMPTZ DEFAULT NOW()
    )
  `);

  await query(`
    CREATE TABLE IF NOT EXISTS kyc_documents (
      id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      user_id          UUID NOT NULL REFERENCES users(id),
      doc_type         VARCHAR(50) NOT NULL,
      file_path        VARCHAR(500),
      status           VARCHAR(30) DEFAULT 'uploaded',
      rejection_reason TEXT,
      reviewed_by      UUID,
      reviewed_at      TIMESTAMPTZ,
      created_at       TIMESTAMPTZ DEFAULT NOW()
    )
  `);

  await query(`
    CREATE TABLE IF NOT EXISTS aml_alerts (
      id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      user_id     UUID NOT NULL REFERENCES users(id),
      alert_type  VARCHAR(50) NOT NULL,
      description TEXT,
      amount      NUMERIC(12,2),
      expense_id  UUID,
      payment_id  UUID,
      status      VARCHAR(20) DEFAULT 'pending',
      reviewed_by UUID,
      reviewed_at TIMESTAMPTZ,
      created_at  TIMESTAMPTZ DEFAULT NOW()
    )
  `);

  await query(`
    CREATE TABLE IF NOT EXISTS gdpr_config (
      key        VARCHAR(100) PRIMARY KEY,
      value      VARCHAR(255),
      updated_at TIMESTAMPTZ DEFAULT NOW()
    )
  `);

  await query(`
    CREATE TABLE IF NOT EXISTS group_invitations (
      id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      group_id        UUID NOT NULL REFERENCES expense_groups(id) ON DELETE CASCADE,
      invited_user_id UUID NOT NULL REFERENCES users(id),
      invited_by_id   UUID NOT NULL REFERENCES users(id),
      status          VARCHAR(20) DEFAULT 'pending',
      created_at      TIMESTAMPTZ DEFAULT NOW()
    )
  `);

  await query(`
    CREATE TABLE IF NOT EXISTS expense_invitations (
      id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      expense_id      UUID NOT NULL REFERENCES expenses(id) ON DELETE CASCADE,
      group_id        UUID REFERENCES expense_groups(id) ON DELETE CASCADE,
      invited_user_id UUID NOT NULL REFERENCES users(id),
      invited_by_id   UUID REFERENCES users(id),
      status          VARCHAR(20) DEFAULT 'pending',
      created_at      TIMESTAMPTZ DEFAULT NOW()
    )
  `);

  await query(`
    CREATE TABLE IF NOT EXISTS payments (
      id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      from_user UUID REFERENCES users(id),
      to_user   UUID REFERENCES users(id),
      amount    NUMERIC(12,2) NOT NULL,
      method    VARCHAR(30) DEFAULT 'in_app',
      note      TEXT,
      space_id  UUID,
      paid_at   TIMESTAMPTZ DEFAULT NOW()
    )
  `);

  await query(`
    CREATE TABLE IF NOT EXISTS fcm_tokens (
      id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      token      TEXT NOT NULL,
      created_at TIMESTAMPTZ DEFAULT NOW(),
      UNIQUE(user_id, token)
    )
  `);

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

  // ── Espaces ──────────────────────────────────────────────────────────────────

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
