CREATE TABLE meter_reading_import_batches (
    id UUID PRIMARY KEY,
    source_device_id VARCHAR(128) NOT NULL,
    source_batch_reference VARCHAR(128) NOT NULL UNIQUE,
    route_id UUID NOT NULL REFERENCES meter_routes(id),
    period VARCHAR(7) NOT NULL,
    total_rows INTEGER NOT NULL,
    imported_rows INTEGER NOT NULL DEFAULT 0,
    skipped_rows INTEGER NOT NULL DEFAULT 0,
    invalid_rows INTEGER NOT NULL DEFAULT 0,
    imported_by VARCHAR(128) NOT NULL,
    imported_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_meter_import_period CHECK (period ~ '^\d{4}-\d{2}$'),
    CONSTRAINT chk_meter_import_counts CHECK (
        total_rows > 0
        AND imported_rows >= 0
        AND skipped_rows >= 0
        AND invalid_rows >= 0
        AND imported_rows + skipped_rows + invalid_rows <= total_rows
    )
);

CREATE INDEX idx_meter_import_batches_route_period ON meter_reading_import_batches(route_id, period);
CREATE INDEX idx_meter_import_batches_device ON meter_reading_import_batches(source_device_id, imported_at);

ALTER TABLE meter_readings
    ADD COLUMN import_batch_id UUID REFERENCES meter_reading_import_batches(id),
    ADD COLUMN source_device_id VARCHAR(128),
    ADD COLUMN source_row_number INTEGER,
    ADD COLUMN locked_at TIMESTAMPTZ,
    ADD COLUMN locked_by VARCHAR(128);

ALTER TABLE meter_readings
    DROP CONSTRAINT chk_meter_readings_status;

ALTER TABLE meter_readings
    ADD CONSTRAINT chk_meter_readings_status CHECK (status IN ('DRAFT','SUBMITTED','VERIFIED','REJECTED','LOCKED')),
    ADD CONSTRAINT chk_meter_readings_lock_trace CHECK (status <> 'LOCKED' OR (locked_at IS NOT NULL AND locked_by IS NOT NULL)),
    ADD CONSTRAINT chk_meter_readings_import_row CHECK (source_row_number IS NULL OR source_row_number > 0);

CREATE INDEX idx_meter_readings_import_batch ON meter_readings(import_batch_id);
CREATE INDEX idx_meter_readings_locked_period ON meter_readings(period, locked_at) WHERE status = 'LOCKED';

CREATE TABLE meter_reading_import_items (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES meter_reading_import_batches(id),
    row_number INTEGER NOT NULL,
    connection_id UUID,
    reading_id UUID REFERENCES meter_readings(id),
    status VARCHAR(32) NOT NULL,
    code VARCHAR(64) NOT NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_meter_import_items_batch_row UNIQUE (batch_id, row_number),
    CONSTRAINT chk_meter_import_items_row CHECK (row_number > 0),
    CONSTRAINT chk_meter_import_items_status CHECK (status IN ('IMPORTED','SKIPPED','INVALID')),
    CONSTRAINT chk_meter_import_items_reading_trace CHECK (
        (status = 'IMPORTED' AND reading_id IS NOT NULL)
        OR (status <> 'IMPORTED' AND reading_id IS NULL)
    )
);

CREATE INDEX idx_meter_import_items_batch_status ON meter_reading_import_items(batch_id, status);
CREATE INDEX idx_meter_import_items_connection ON meter_reading_import_items(connection_id);
