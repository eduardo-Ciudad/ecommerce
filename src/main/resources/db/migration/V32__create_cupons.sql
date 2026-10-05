CREATE TABLE coupons (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL,
    discount_percent DECIMAL(5, 2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_coupons_code UNIQUE (code),
    CONSTRAINT ck_coupons_discount_percent CHECK (discount_percent > 0 AND discount_percent <= 90)
);