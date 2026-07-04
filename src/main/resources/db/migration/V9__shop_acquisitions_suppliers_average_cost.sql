-- =============================================
-- Kolo Boutique - Approvisionnement, fournisseurs et coût moyen pondéré
-- =============================================

-- 1. Enrichir shop_suppliers
ALTER TABLE shop_suppliers ADD COLUMN IF NOT EXISTS normalized_name VARCHAR(255);
ALTER TABLE shop_suppliers ADD COLUMN IF NOT EXISTS outstanding_balance BIGINT NOT NULL DEFAULT 0;
ALTER TABLE shop_suppliers ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT NOW();

UPDATE shop_suppliers SET normalized_name = LOWER(TRIM(name)) WHERE normalized_name IS NULL;

CREATE INDEX IF NOT EXISTS idx_shop_suppliers_org_active ON shop_suppliers(organization_id, active);

-- 2. Coût moyen pondéré sur les produits
ALTER TABLE shop_products ADD COLUMN IF NOT EXISTS average_cost BIGINT NOT NULL DEFAULT 0;
UPDATE shop_products SET average_cost = purchase_price WHERE average_cost = 0 AND purchase_price > 0;

-- 3. Table des acquisitions (approvisionnements)
CREATE TABLE IF NOT EXISTS shop_acquisitions (
    id                BIGSERIAL PRIMARY KEY,
    organization_id   BIGINT NOT NULL REFERENCES organizations(id),
    supplier_id       BIGINT REFERENCES shop_suppliers(id),
    recorded_by_id    BIGINT NOT NULL REFERENCES users(id),
    acquisition_type  VARCHAR(30) NOT NULL DEFAULT 'CASH',
    total_amount      BIGINT NOT NULL DEFAULT 0,
    paid_amount       BIGINT NOT NULL DEFAULT 0,
    due_amount        BIGINT NOT NULL DEFAULT 0,
    due_date          DATE,
    note              VARCHAR(500),
    status            VARCHAR(30) NOT NULL DEFAULT 'CONFIRMED',
    confirmed_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_shop_acquisitions_org_date ON shop_acquisitions(organization_id, confirmed_at);
CREATE INDEX IF NOT EXISTS idx_shop_acquisitions_supplier ON shop_acquisitions(supplier_id);

-- 4. Lignes d'acquisition
CREATE TABLE IF NOT EXISTS shop_acquisition_items (
    id                BIGSERIAL PRIMARY KEY,
    acquisition_id    BIGINT NOT NULL REFERENCES shop_acquisitions(id) ON DELETE CASCADE,
    product_id        BIGINT NOT NULL REFERENCES shop_products(id),
    product_name      VARCHAR(255) NOT NULL,
    quantity          BIGINT NOT NULL,
    unit_cost         BIGINT NOT NULL,
    line_total        BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_shop_acquisition_items_acq ON shop_acquisition_items(acquisition_id);
CREATE INDEX IF NOT EXISTS idx_shop_acquisition_items_product ON shop_acquisition_items(product_id);

-- 5. Paiements fournisseurs
CREATE TABLE IF NOT EXISTS shop_supplier_payments (
    id                BIGSERIAL PRIMARY KEY,
    organization_id   BIGINT NOT NULL REFERENCES organizations(id),
    supplier_id       BIGINT NOT NULL REFERENCES shop_suppliers(id),
    acquisition_id    BIGINT REFERENCES shop_acquisitions(id),
    recorded_by_id    BIGINT NOT NULL REFERENCES users(id),
    amount            BIGINT NOT NULL,
    payment_method    VARCHAR(30) NOT NULL DEFAULT 'CASH',
    note              VARCHAR(500),
    created_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_shop_supplier_payments_org ON shop_supplier_payments(organization_id);
CREATE INDEX IF NOT EXISTS idx_shop_supplier_payments_supplier ON shop_supplier_payments(supplier_id);
