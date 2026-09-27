-- =============================================
-- Kolo Boutique - Fondation multi-canaux et commandes conversationnelles
-- Migration additive : l'ancien routage reste compatible.
-- =============================================

CREATE TABLE IF NOT EXISTS whatsapp_channels (
    id                    BIGSERIAL PRIMARY KEY,
    organization_id       BIGINT NOT NULL REFERENCES organizations(id),
    provider              VARCHAR(30) NOT NULL DEFAULT 'GOWA',
    device_id             VARCHAR(255),
    phone_number          VARCHAR(30),
    display_name          VARCHAR(255),
    webhook_secret_hash   VARCHAR(255),
    status                VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    metadata              TEXT,
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (provider, device_id),
    UNIQUE (provider, phone_number)
);

CREATE INDEX IF NOT EXISTS idx_whatsapp_channels_org_status
    ON whatsapp_channels(organization_id, status);

CREATE TABLE IF NOT EXISTS conversation_commands (
    id                    BIGSERIAL PRIMARY KEY,
    organization_id       BIGINT NOT NULL REFERENCES organizations(id),
    channel_id            BIGINT REFERENCES whatsapp_channels(id),
    actor_user_id         BIGINT REFERENCES users(id),
    source_message_id     VARCHAR(255),
    idempotency_key       VARCHAR(512) NOT NULL,
    intent                VARCHAR(80) NOT NULL,
    status                VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',
    confidence            NUMERIC(5,4),
    normalized_payload    TEXT NOT NULL,
    result_payload        TEXT,
    error_code             VARCHAR(100),
    error_message          VARCHAR(1000),
    created_at             TIMESTAMP NOT NULL DEFAULT NOW(),
    started_at             TIMESTAMP,
    completed_at           TIMESTAMP,
    updated_at             TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (organization_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_conversation_commands_channel_created
    ON conversation_commands(channel_id, created_at);
CREATE INDEX IF NOT EXISTS idx_conversation_commands_actor_created
    ON conversation_commands(actor_user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_conversation_commands_status
    ON conversation_commands(status, created_at);

CREATE TABLE IF NOT EXISTS conversation_messages (
    id                    BIGSERIAL PRIMARY KEY,
    command_id            BIGINT REFERENCES conversation_commands(id),
    organization_id       BIGINT NOT NULL REFERENCES organizations(id),
    channel_id            BIGINT REFERENCES whatsapp_channels(id),
    actor_user_id         BIGINT REFERENCES users(id),
    external_message_id   VARCHAR(255),
    direction             VARCHAR(20) NOT NULL,
    message_type          VARCHAR(30) NOT NULL DEFAULT 'TEXT',
    body                  TEXT,
    raw_payload           TEXT,
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (channel_id, external_message_id)
);

CREATE INDEX IF NOT EXISTS idx_conversation_messages_org_created
    ON conversation_messages(organization_id, created_at);
CREATE INDEX IF NOT EXISTS idx_conversation_messages_command
    ON conversation_messages(command_id);

CREATE TABLE IF NOT EXISTS shop_product_units (
    id                    BIGSERIAL PRIMARY KEY,
    organization_id       BIGINT NOT NULL REFERENCES organizations(id),
    product_id            BIGINT NOT NULL REFERENCES shop_products(id) ON DELETE CASCADE,
    name                  VARCHAR(50) NOT NULL,
    normalized_name       VARCHAR(50) NOT NULL,
    conversion_to_base    NUMERIC(20,6) NOT NULL,
    is_base               BOOLEAN NOT NULL DEFAULT FALSE,
    sale_allowed          BOOLEAN NOT NULL DEFAULT TRUE,
    purchase_allowed      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (product_id, normalized_name)
);

CREATE INDEX IF NOT EXISTS idx_shop_product_units_org_product
    ON shop_product_units(organization_id, product_id);

CREATE OR REPLACE FUNCTION update_whatsapp_channel_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_whatsapp_channels_updated_at ON whatsapp_channels;
CREATE TRIGGER trg_whatsapp_channels_updated_at
    BEFORE UPDATE ON whatsapp_channels
    FOR EACH ROW EXECUTE FUNCTION update_whatsapp_channel_updated_at();

CREATE OR REPLACE FUNCTION update_conversation_command_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_conversation_commands_updated_at ON conversation_commands;
CREATE TRIGGER trg_conversation_commands_updated_at
    BEFORE UPDATE ON conversation_commands
    FOR EACH ROW EXECUTE FUNCTION update_conversation_command_updated_at();
