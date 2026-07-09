CREATE TABLE payment_reconciliation_handoff_acknowledgements (
    id UUID PRIMARY KEY,
    packet_scope_hash VARCHAR(128) NOT NULL UNIQUE,
    filter_snapshot TEXT NOT NULL,
    stale_note_count BIGINT NOT NULL,
    owner_count BIGINT NOT NULL,
    max_overdue_days BIGINT NOT NULL,
    acknowledged_by VARCHAR(128) NOT NULL,
    acknowledged_at TIMESTAMPTZ NOT NULL,
    reason TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_payment_reconciliation_handoff_ack_counts CHECK (
        stale_note_count > 0
        AND owner_count > 0
        AND max_overdue_days > 0
    ),
    CONSTRAINT chk_payment_reconciliation_handoff_ack_hash CHECK (length(btrim(packet_scope_hash)) > 0),
    CONSTRAINT chk_payment_reconciliation_handoff_ack_filter CHECK (
        length(btrim(filter_snapshot)) > 0
        AND char_length(filter_snapshot) <= 2000
    ),
    CONSTRAINT chk_payment_reconciliation_handoff_ack_actor CHECK (length(btrim(acknowledged_by)) > 0),
    CONSTRAINT chk_payment_reconciliation_handoff_ack_reason CHECK (
        length(btrim(reason)) > 0
        AND char_length(reason) <= 500
    )
);

CREATE INDEX idx_payment_reconciliation_handoff_ack_at
    ON payment_reconciliation_handoff_acknowledgements(acknowledged_at DESC);

CREATE INDEX idx_payment_reconciliation_handoff_ack_actor
    ON payment_reconciliation_handoff_acknowledgements(acknowledged_by, acknowledged_at DESC);
