-- =============================================
-- Kolo Boutique - Dépenses boutique
-- =============================================

CREATE TABLE IF NOT EXISTS shop_expenses (
    id                BIGSERIAL PRIMARY KEY,
    organization_id   BIGINT NOT NULL REFERENCES organizations(id),
    recorded_by_id    BIGINT NOT NULL REFERENCES users(id),
    amount            BIGINT NOT NULL,
    description       VARCHAR(500) NOT NULL,
    category          VARCHAR(50) NOT NULL DEFAULT 'DIVERS',
    status            VARCHAR(30) NOT NULL DEFAULT 'CONFIRMED',
    confirmed_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_shop_expenses_org_date ON shop_expenses(organization_id, confirmed_at);
CREATE INDEX IF NOT EXISTS idx_shop_expenses_user ON shop_expenses(recorded_by_id);
