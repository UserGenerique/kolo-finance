-- =============================================
-- Kolo Boutique - Masquer les clients réservés résiduels
-- =============================================

UPDATE shop_customers
SET active = FALSE
WHERE normalized_name IN ('oui', 'non', 'ok', 'annuler', 'valider', 'confirmer')
  AND phone_number IS NULL
  AND COALESCE(credit_limit, 0) = 0;
