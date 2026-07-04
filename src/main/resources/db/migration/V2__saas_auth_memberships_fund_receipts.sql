-- =============================================
-- Kolo Finance - SaaS auth, memberships and fund receipts
-- =============================================

ALTER TABLE users ALTER COLUMN organization_id DROP NOT NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_set_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP;

ALTER TABLE organizations ADD COLUMN IF NOT EXISTS subscription_plan VARCHAR(30) NOT NULL DEFAULT 'STARTER';
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS subscription_status VARCHAR(30) NOT NULL DEFAULT 'TRIAL';
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS subscription_started_at TIMESTAMP;
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS subscription_ends_at TIMESTAMP;
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS max_agents INTEGER NOT NULL DEFAULT 3;

CREATE TABLE IF NOT EXISTS organization_memberships (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    user_id         BIGINT NOT NULL REFERENCES users(id),
    role            VARCHAR(20) NOT NULL CHECK (role IN ('BOSS', 'MANAGER', 'AGENT')),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (organization_id, user_id)
);

INSERT INTO organization_memberships (organization_id, user_id, role, active, created_at)
SELECT organization_id, id, role, active, created_at
FROM users
WHERE organization_id IS NOT NULL
ON CONFLICT (organization_id, user_id) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_memberships_user ON organization_memberships(user_id);
CREATE INDEX IF NOT EXISTS idx_memberships_org ON organization_memberships(organization_id);

ALTER TABLE funds ADD COLUMN IF NOT EXISTS status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE funds ADD COLUMN IF NOT EXISTS receipt_confirmed_at TIMESTAMP;
ALTER TABLE funds ADD COLUMN IF NOT EXISTS receipt_rejected_at TIMESTAMP;
ALTER TABLE funds ADD COLUMN IF NOT EXISTS receipt_note VARCHAR(500);

UPDATE funds SET status = 'ACTIVE' WHERE status IS NULL;
CREATE INDEX IF NOT EXISTS idx_funds_agent_status ON funds(agent_id, status);

CREATE TABLE IF NOT EXISTS platform_admins (
    id              BIGSERIAL PRIMARY KEY,
    phone_number    VARCHAR(20) NOT NULL UNIQUE,
    name            VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    last_login_at   TIMESTAMP
);

CREATE TABLE IF NOT EXISTS auth_sessions (
    id                BIGSERIAL PRIMARY KEY,
    token_hash        VARCHAR(128) NOT NULL UNIQUE,
    user_type         VARCHAR(30) NOT NULL,
    platform_admin_id BIGINT REFERENCES platform_admins(id),
    user_id           BIGINT REFERENCES users(id),
    organization_id   BIGINT REFERENCES organizations(id),
    expires_at        TIMESTAMP NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_auth_sessions_hash ON auth_sessions(token_hash);
CREATE INDEX IF NOT EXISTS idx_auth_sessions_user ON auth_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_auth_sessions_admin ON auth_sessions(platform_admin_id);
