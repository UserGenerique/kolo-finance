-- =============================================
-- Kolo Boutique - Unicité client par numéro et organisation
-- =============================================

DROP INDEX IF EXISTS idx_shop_customers_org_normalized;
CREATE INDEX IF NOT EXISTS idx_shop_customers_org_normalized_lookup
    ON shop_customers(organization_id, normalized_name);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM shop_customers
        WHERE active = TRUE
          AND phone_number IS NOT NULL
          AND phone_number <> ''
        GROUP BY organization_id, phone_number
        HAVING COUNT(*) > 1
    ) THEN
        CREATE UNIQUE INDEX IF NOT EXISTS idx_shop_customers_org_phone_active_unique
            ON shop_customers(organization_id, phone_number)
            WHERE active = TRUE AND phone_number IS NOT NULL AND phone_number <> '';
    END IF;
END $$;
