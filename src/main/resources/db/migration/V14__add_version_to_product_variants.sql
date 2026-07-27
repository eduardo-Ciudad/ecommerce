-- V__add_version_to_product_variants.sql
ALTER TABLE product_variants ADD COLUMN version BIGINT NOT NULL DEFAULT 0;