-- =============================================
-- Kolo Boutique - Ajout du prix de gros
-- =============================================

ALTER TABLE shop_products ADD COLUMN IF NOT EXISTS wholesale_price BIGINT NOT NULL DEFAULT 0;
