-- Sync de pedidos site -> Bling (issue #9)

ALTER TABLE orders
    ADD COLUMN paid_at               TIMESTAMP,
    ADD COLUMN payment_method        VARCHAR(20),
    ADD COLUMN customer_document     VARCHAR(14),
    ADD COLUMN bling_order_id        BIGINT,
    ADD COLUMN bling_sync_status     VARCHAR(20),
    ADD COLUMN bling_sync_attempts   INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN bling_sync_error      VARCHAR(500),
    ADD COLUMN bling_sync_started_at TIMESTAMP,
    ADD COLUMN bling_synced_at       TIMESTAMP;

-- Um pedido do site nunca pode apontar para dois pedidos no Bling (e vice-versa)
ALTER TABLE orders
    ADD CONSTRAINT uk_orders_bling_order_id UNIQUE (bling_order_id);

-- Índice parcial para o scheduler de retry: só pedidos ainda na fila do Bling
CREATE INDEX idx_orders_bling_sync_pending
    ON orders (bling_sync_status, bling_sync_attempts)
    WHERE bling_sync_status IN ('PENDING', 'PROCESSING', 'CREATED', 'FAILED');