-- =============================================
-- Nettoyage ciblé: clients accidentels "oui/non" créés pendant le test crédit
-- =============================================

WITH invalid_customers AS (
    SELECT id, organization_id
    FROM shop_customers
    WHERE normalized_name IN ('oui', 'non')
      AND phone_number IS NULL
      AND credit_limit = 0
      AND outstanding_balance > 0
      AND created_at >= NOW() - INTERVAL '7 days'
),
invalid_sales AS (
    SELECT s.id, s.organization_id
    FROM shop_sales s
    JOIN invalid_customers c ON c.id = s.customer_id
    WHERE s.sale_type = 'CREDIT'
      AND s.due_amount > 0
      AND s.created_at >= NOW() - INTERVAL '7 days'
),
restored_stock AS (
    UPDATE shop_products p
    SET stock_quantity = p.stock_quantity + x.quantity
    FROM (
        SELECT si.product_id, SUM(si.quantity) AS quantity
        FROM shop_sale_items si
        JOIN invalid_sales s ON s.id = si.sale_id
        GROUP BY si.product_id
    ) x
    WHERE p.id = x.product_id
    RETURNING p.id
)
DELETE FROM shop_stock_movements m
USING invalid_sales s
WHERE m.reference_type = 'SHOP_SALE'
  AND m.reference_id = s.id;

WITH invalid_customers AS (
    SELECT id, organization_id
    FROM shop_customers
    WHERE normalized_name IN ('oui', 'non')
      AND phone_number IS NULL
      AND credit_limit = 0
      AND outstanding_balance > 0
      AND created_at >= NOW() - INTERVAL '7 days'
),
invalid_sales AS (
    SELECT s.id
    FROM shop_sales s
    JOIN invalid_customers c ON c.id = s.customer_id
    WHERE s.sale_type = 'CREDIT'
      AND s.due_amount > 0
      AND s.created_at >= NOW() - INTERVAL '7 days'
)
DELETE FROM shop_customer_payments p
USING invalid_sales s
WHERE p.sale_id = s.id;

WITH invalid_customers AS (
    SELECT id, organization_id
    FROM shop_customers
    WHERE normalized_name IN ('oui', 'non')
      AND phone_number IS NULL
      AND credit_limit = 0
      AND outstanding_balance > 0
      AND created_at >= NOW() - INTERVAL '7 days'
),
invalid_sales AS (
    SELECT s.id
    FROM shop_sales s
    JOIN invalid_customers c ON c.id = s.customer_id
    WHERE s.sale_type = 'CREDIT'
      AND s.due_amount > 0
      AND s.created_at >= NOW() - INTERVAL '7 days'
)
DELETE FROM shop_sales s
USING invalid_sales x
WHERE s.id = x.id;

DELETE FROM shop_customers c
WHERE c.normalized_name IN ('oui', 'non')
  AND c.phone_number IS NULL
  AND c.credit_limit = 0
  AND c.outstanding_balance > 0
  AND c.created_at >= NOW() - INTERVAL '7 days'
  AND NOT EXISTS (
      SELECT 1
      FROM shop_sales s
      WHERE s.customer_id = c.id
  )
  AND NOT EXISTS (
      SELECT 1
      FROM shop_customer_payments p
      WHERE p.customer_id = c.id
  );
