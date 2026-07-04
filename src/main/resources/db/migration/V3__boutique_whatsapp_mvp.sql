-- =============================================
-- Kolo Boutique - WhatsApp-only MVP
-- =============================================

CREATE TABLE IF NOT EXISTS shop_products (
    id                  BIGSERIAL PRIMARY KEY,
    organization_id     BIGINT NOT NULL REFERENCES organizations(id),
    name                VARCHAR(255) NOT NULL,
    normalized_name     VARCHAR(255) NOT NULL,
    aliases             VARCHAR(500),
    category            VARCHAR(100),
    unit                VARCHAR(30) NOT NULL DEFAULT 'piece',
    purchase_price      BIGINT NOT NULL DEFAULT 0,
    sale_price          BIGINT NOT NULL,
    stock_quantity      BIGINT NOT NULL DEFAULT 0,
    min_stock_quantity  BIGINT NOT NULL DEFAULT 0,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (organization_id, normalized_name)
);

CREATE INDEX IF NOT EXISTS idx_shop_products_org_active ON shop_products(organization_id, active);

CREATE TABLE IF NOT EXISTS shop_sales (
    id                BIGSERIAL PRIMARY KEY,
    organization_id   BIGINT NOT NULL REFERENCES organizations(id),
    seller_id         BIGINT NOT NULL REFERENCES users(id),
    status            VARCHAR(30) NOT NULL DEFAULT 'CONFIRMED',
    sale_type         VARCHAR(30) NOT NULL DEFAULT 'QUICK',
    payment_method    VARCHAR(30) NOT NULL DEFAULT 'CASH',
    subtotal_amount   BIGINT NOT NULL DEFAULT 0,
    discount_amount   BIGINT NOT NULL DEFAULT 0,
    total_amount      BIGINT NOT NULL DEFAULT 0,
    profit_amount     BIGINT NOT NULL DEFAULT 0,
    confirmed_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_shop_sales_org_date ON shop_sales(organization_id, confirmed_at);
CREATE INDEX IF NOT EXISTS idx_shop_sales_seller_date ON shop_sales(seller_id, confirmed_at);

CREATE TABLE IF NOT EXISTS shop_sale_items (
    id              BIGSERIAL PRIMARY KEY,
    sale_id         BIGINT NOT NULL REFERENCES shop_sales(id) ON DELETE CASCADE,
    product_id      BIGINT NOT NULL REFERENCES shop_products(id),
    product_name    VARCHAR(255) NOT NULL,
    quantity        BIGINT NOT NULL,
    unit_price      BIGINT NOT NULL,
    purchase_price  BIGINT NOT NULL DEFAULT 0,
    line_total      BIGINT NOT NULL,
    line_profit     BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_shop_sale_items_sale ON shop_sale_items(sale_id);
CREATE INDEX IF NOT EXISTS idx_shop_sale_items_product ON shop_sale_items(product_id);

CREATE TABLE IF NOT EXISTS shop_stock_movements (
    id                BIGSERIAL PRIMARY KEY,
    organization_id   BIGINT NOT NULL REFERENCES organizations(id),
    product_id        BIGINT NOT NULL REFERENCES shop_products(id),
    user_id           BIGINT REFERENCES users(id),
    movement_type     VARCHAR(30) NOT NULL,
    quantity_delta    BIGINT NOT NULL,
    previous_stock    BIGINT NOT NULL,
    new_stock         BIGINT NOT NULL,
    reference_type    VARCHAR(50),
    reference_id      BIGINT,
    note              VARCHAR(500),
    created_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_shop_stock_movements_org_date ON shop_stock_movements(organization_id, created_at);
CREATE INDEX IF NOT EXISTS idx_shop_stock_movements_product ON shop_stock_movements(product_id);

CREATE TABLE IF NOT EXISTS shop_conversation_sessions (
    id                BIGSERIAL PRIMARY KEY,
    organization_id   BIGINT NOT NULL REFERENCES organizations(id),
    user_id           BIGINT NOT NULL REFERENCES users(id),
    session_type      VARCHAR(50) NOT NULL,
    state             VARCHAR(50) NOT NULL,
    payload           TEXT,
    expires_at        TIMESTAMP NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_shop_sessions_user_type ON shop_conversation_sessions(user_id, session_type, state, expires_at);

CREATE TABLE IF NOT EXISTS shop_customers (
    id                BIGSERIAL PRIMARY KEY,
    organization_id   BIGINT NOT NULL REFERENCES organizations(id),
    name              VARCHAR(255) NOT NULL,
    phone_number      VARCHAR(30),
    credit_limit      BIGINT NOT NULL DEFAULT 0,
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_shop_customers_org ON shop_customers(organization_id);

CREATE TABLE IF NOT EXISTS shop_suppliers (
    id                BIGSERIAL PRIMARY KEY,
    organization_id   BIGINT NOT NULL REFERENCES organizations(id),
    name              VARCHAR(255) NOT NULL,
    phone_number      VARCHAR(30),
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_shop_suppliers_org ON shop_suppliers(organization_id);
