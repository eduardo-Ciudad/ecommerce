ALTER TABLE order_items
    ADD COLUMN product_name VARCHAR(150),
    ADD COLUMN size VARCHAR(100),
    ADD COLUMN color VARCHAR(100),
    ADD COLUMN sku VARCHAR(100),
    ADD COLUMN gtin VARCHAR(14);

UPDATE order_items oi
SET product_name = p.name,
    size  = pv.size,
    color = pv.color,
    sku   = pv.sku,
    gtin  = pv.gtin
FROM product_variants pv
JOIN products p ON p.id = pv.product_id
WHERE oi.variant_id = pv.id;