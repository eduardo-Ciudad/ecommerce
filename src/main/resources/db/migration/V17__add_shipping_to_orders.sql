-- V17__add_shipping_to_orders.sql

ALTER TABLE orders
    ADD COLUMN shipping_method VARCHAR(20),
    ADD COLUMN shipping_price NUMERIC(10,2),
    ADD COLUMN shipping_deadline_days INTEGER,
    ADD COLUMN recipient_name VARCHAR(100),
    ADD COLUMN recipient_cep VARCHAR(9),
    ADD COLUMN recipient_street VARCHAR(200),
    ADD COLUMN recipient_number VARCHAR(20),
    ADD COLUMN recipient_complement VARCHAR(100),
    ADD COLUMN recipient_neighborhood VARCHAR(100),
    ADD COLUMN recipient_city VARCHAR(100),
    ADD COLUMN recipient_state VARCHAR(2);