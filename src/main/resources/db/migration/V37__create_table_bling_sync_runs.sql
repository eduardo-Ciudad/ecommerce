CREATE TABLE bling_sync_runs (
    id UUID PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    step VARCHAR(20) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    categories_synced INTEGER NOT NULL DEFAULT 0,
    total_items INTEGER NOT NULL DEFAULT 0,
    processed_items INTEGER NOT NULL DEFAULT 0,
    variants_synced INTEGER NOT NULL DEFAULT 0,
    variants_skipped INTEGER NOT NULL DEFAULT 0,
    standalone_synced INTEGER NOT NULL DEFAULT 0,
    standalone_skipped INTEGER NOT NULL DEFAULT 0,
    deactivated INTEGER NOT NULL DEFAULT 0,
    error_message TEXT
);

CREATE INDEX idx_bling_sync_runs_started_at ON bling_sync_runs (started_at DESC);