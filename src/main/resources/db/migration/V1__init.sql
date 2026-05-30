-- =============================================
-- Kolo Finance MVP - Schema initial
-- =============================================

CREATE TABLE IF NOT EXISTS organizations (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS users (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    phone_number    VARCHAR(20) NOT NULL UNIQUE,
    name            VARCHAR(255) NOT NULL,
    role            VARCHAR(20) NOT NULL CHECK (role IN ('BOSS', 'MANAGER', 'AGENT')),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_phone ON users(phone_number);
CREATE INDEX idx_users_org ON users(organization_id);

CREATE TABLE IF NOT EXISTS funds (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    agent_id        BIGINT NOT NULL REFERENCES users(id),
    initial_amount  BIGINT NOT NULL,
    balance         BIGINT NOT NULL,
    description     VARCHAR(500),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_funds_agent ON funds(agent_id);

CREATE TABLE IF NOT EXISTS expenses (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    agent_id        BIGINT NOT NULL REFERENCES users(id),
    fund_id         BIGINT NOT NULL REFERENCES funds(id),
    amount          BIGINT NOT NULL,
    description     VARCHAR(500) NOT NULL,
    category        VARCHAR(100),
    confirmed_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_expenses_org ON expenses(organization_id);
CREATE INDEX idx_expenses_agent ON expenses(agent_id);

CREATE TABLE IF NOT EXISTS draft_expenses (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    agent_id        BIGINT NOT NULL REFERENCES users(id),
    fund_id         BIGINT REFERENCES funds(id),
    amount          BIGINT NOT NULL,
    description     VARCHAR(500) NOT NULL,
    category        VARCHAR(100),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED')),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_drafts_agent_status ON draft_expenses(agent_id, status);
