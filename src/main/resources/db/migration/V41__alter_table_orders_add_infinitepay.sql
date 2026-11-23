ALTER TABLE orders ADD COLUMN payment_provider VARCHAR(20) NOT NULL DEFAULT 'MERCADO_PAGO';
ALTER TABLE orders ADD COLUMN payment_transaction_id VARCHAR(100);
ALTER TABLE orders ADD COLUMN payment_installments INTEGER;