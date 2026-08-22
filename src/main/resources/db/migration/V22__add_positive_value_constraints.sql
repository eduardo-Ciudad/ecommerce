-- V22__add_positive_value_constraints.sql

-- ============================================================
-- ANTES DE APLICAR: rode estas queries manualmente para
-- confirmar que não existem dados que violem as constraints.
-- Se algum SELECT retornar linhas, resolva os dados antes
-- de aplicar esta migration.
-- ============================================================
--
-- SELECT * FROM orders WHERE total <= 0;
-- SELECT * FROM orders WHERE status NOT IN ('PENDING', 'PAID', 'SHIPPED', 'DELIVERED', 'CANCELLED');
-- SELECT * FROM order_items WHERE unit_price <= 0 OR quantity <= 0;
-- SELECT * FROM product_variants WHERE price <= 0 OR stock < 0;
-- SELECT * FROM cart_items WHERE quantity <= 0;
--
-- ============================================================

ALTER TABLE orders
    ADD CONSTRAINT orders_total_positive CHECK (total > 0),
    ADD CONSTRAINT orders_status_valid CHECK (
        status IN ('PENDING', 'PAID', 'SHIPPED', 'DELIVERED', 'CANCELLED')
    );

ALTER TABLE order_items
    ADD CONSTRAINT order_items_unit_price_positive CHECK (unit_price > 0),
    ADD CONSTRAINT order_items_quantity_positive CHECK (quantity > 0);

ALTER TABLE product_variants
    ADD CONSTRAINT product_variants_price_positive CHECK (price > 0),
    ADD CONSTRAINT product_variants_stock_non_negative CHECK (stock >= 0);

ALTER TABLE cart_items
    ADD CONSTRAINT cart_items_quantity_positive CHECK (quantity > 0);

-- NOTA: não foi adicionada constraint no status de pagamento (payment_status
-- ou campo equivalente), pois ele reflete valores livres vindos diretamente
-- do provedor (Mercado Pago), sem enum fixo no código. Travar esse campo
-- arrisca quebrar o webhook caso o provedor use um valor não previsto.
-- Ver AUD-005 / seção 8 da auditoria (status do provedor armazenado como
-- String sem enum/conversor) para tratar isso de forma adequada antes de
-- considerar uma constraint aqui.