-- =============================================
-- Kolo Boutique - Clients, ventes à crédit et paiements
-- =============================================

ALTER TABLE shop_customers
    ADD COLUMN IF NOT EXISTS normalized_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS outstanding_balance BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT NOW();

UPDATE shop_customers
SET normalized_name = lower(trim(regexp_replace(name, '[^[:alnum:]]+', ' ', 'g')))
WHERE normalized_name IS NULL OR normalized_name = '';

ALTER TABLE shop_customers
    ALTER COLUMN normalized_name SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_shop_customers_org_active ON shop_customers(organization_id, active);
CREATE INDEX IF NOT EXISTS idx_shop_customers_org_balance ON shop_customers(organization_id, outstanding_balance);
CREATE UNIQUE INDEX IF NOT EXISTS idx_shop_customers_org_normalized ON shop_customers(organization_id, normalized_name);

ALTER TABLE shop_sales
    ADD COLUMN IF NOT EXISTS customer_id BIGINT REFERENCES shop_customers(id),
    ADD COLUMN IF NOT EXISTS paid_amount BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS due_amount BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS due_date DATE,
    ADD COLUMN IF NOT EXISTS note VARCHAR(500);

UPDATE shop_sales
SET paid_amount = total_amount
WHERE paid_amount = 0 AND due_amount = 0 AND sale_type = 'QUICK';

CREATE INDEX IF NOT EXISTS idx_shop_sales_customer_due ON shop_sales(customer_id, due_amount);

CREATE TABLE IF NOT EXISTS shop_customer_payments (
    id                BIGSERIAL PRIMARY KEY,
    organization_id   BIGINT NOT NULL REFERENCES organizations(id),
    customer_id       BIGINT NOT NULL REFERENCES shop_customers(id),
    sale_id           BIGINT REFERENCES shop_sales(id),
    recorded_by_id    BIGINT REFERENCES users(id),
    amount            BIGINT NOT NULL,
    payment_method    VARCHAR(30) NOT NULL DEFAULT 'CASH',
    note              VARCHAR(500),
    paid_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_shop_customer_payments_org_date ON shop_customer_payments(organization_id, paid_at);
CREATE INDEX IF NOT EXISTS idx_shop_customer_payments_customer ON shop_customer_payments(customer_id, paid_at);
