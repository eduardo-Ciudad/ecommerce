-- V23__add_unique_default_address_index.sql


CREATE UNIQUE INDEX addresses_one_default_per_user
    ON addresses (user_id)
    WHERE is_default = TRUE;