-- Venda local pelo admin (balcão)
ALTER TABLE orders
    ADD COLUMN channel       VARCHAR(10) NOT NULL DEFAULT 'SITE',
    ADD COLUMN customer_name VARCHAR(100);

ALTER TABLE orders
    ADD CONSTRAINT ck_orders_channel CHECK (channel IN ('SITE', 'LOCAL'));