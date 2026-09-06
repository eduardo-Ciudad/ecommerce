ALTER TABLE categories
    ADD COLUMN parent_id UUID;

ALTER TABLE categories
    ADD CONSTRAINT fk_categories_parent
        FOREIGN KEY (parent_id)
        REFERENCES categories(id);

CREATE INDEX idx_categories_parent_id
    ON categories(parent_id);