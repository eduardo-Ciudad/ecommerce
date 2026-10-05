ALTER TABLE orders
    ADD COLUMN coupon_code VARCHAR(50),
    ADD COLUMN discount_amount DECIMAL(10, 2) NOT NULL DEFAULT 0;