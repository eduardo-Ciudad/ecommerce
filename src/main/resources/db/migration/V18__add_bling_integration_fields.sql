-- V18: campos de integração com o Bling
-- Product: referência ao produto pai no Bling
-- ProductVariant: referência à variação no Bling + SKU

ALTER TABLE products
    ADD COLUMN bling_product_id BIGINT UNIQUE;

ALTER TABLE product_variants
    ADD COLUMN bling_variation_id BIGINT UNIQUE,
    ADD COLUMN sku VARCHAR(100) UNIQUE;