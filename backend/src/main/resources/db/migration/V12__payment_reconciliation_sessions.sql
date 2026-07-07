CREATE TABLE payment_reconciliation_sessions (
    id UUID PRIMARY KEY,
    session_number VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    source_filename VARCHAR(255),
    bank_account_reference VARCHAR(128),
    created_by VARCHAR(128) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    total_rows INTEGER NOT NULL DEFAULT 0,
    exact_matches INTEGER NOT NULL DEFAULT 0,
    probable_matches INTEGER NOT NULL DEFAULT 0,
    amount_variances INTEGER NOT NULL DEFAULT 0,
    reversed_payments INTEGER NOT NULL DEFAULT 0,
    multiple_candidates INTEGER NOT NULL DEFAULT 0,
    unmatched_rows INTEGER NOT NULL DEFAULT 0,
    total_variance NUMERIC(19,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_payment_reconciliation_sessions_status CHECK (status IN ('OPEN','COMPLETED','CANCELLED')),
    CONSTRAINT chk_payment_reconciliation_sessions_counts CHECK (
        total_rows >= 0
        AND exact_matches >= 0
        AND probable_matches >= 0
        AND amount_variances >= 0
        AND reversed_payments >= 0
        AND multiple_candidates >= 0
        AND unmatched_rows >= 0
    )
);

CREATE INDEX idx_payment_reconciliation_sessions_status_started
    ON payment_reconciliation_sessions(status, started_at DESC);

CREATE TABLE payment_reconciliation_items (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES payment_reconciliation_sessions(id),
    row_number INTEGER NOT NULL,
    statement_reference VARCHAR(128) NOT NULL,
    statement_amount NUMERIC(19,2) NOT NULL,
    transacted_at TIMESTAMPTZ NOT NULL,
    statement_channel VARCHAR(64),
    match_status VARCHAR(32) NOT NULL,
    amount_variance NUMERIC(19,2),
    candidate_count INTEGER NOT NULL DEFAULT 0,
    matched_payment_id UUID REFERENCES payments(id),
    matched_payment_number VARCHAR(64),
    matched_payment_status VARCHAR(32),
    matched_payment_amount NUMERIC(19,2),
    matched_payment_paid_at TIMESTAMPTZ,
    matched_payment_channel VARCHAR(64),
    settlement_journal_entry_id UUID,
    reversal_journal_entry_id UUID,
    resolution_status VARCHAR(32) NOT NULL,
    resolution_reason TEXT,
    resolved_by VARCHAR(128),
    resolved_at TIMESTAMPTZ,
    message TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_payment_reconciliation_items_session_row UNIQUE (session_id, row_number),
    CONSTRAINT chk_payment_reconciliation_items_statement_amount CHECK (statement_amount > 0),
    CONSTRAINT chk_payment_reconciliation_items_candidate_count CHECK (candidate_count >= 0),
    CONSTRAINT chk_payment_reconciliation_items_match_status CHECK (
        match_status IN ('EXACT_MATCH','PROBABLE_MATCH','AMOUNT_VARIANCE','REVERSED_PAYMENT','MULTIPLE_CANDIDATES','UNMATCHED')
    ),
    CONSTRAINT chk_payment_reconciliation_items_resolution_status CHECK (resolution_status IN ('OPEN','ACCEPTED','RESOLVED','IGNORED')),
    CONSTRAINT chk_payment_reconciliation_items_matched_payment_status CHECK (
        matched_payment_status IS NULL OR matched_payment_status IN ('PENDING','SETTLED','REVERSED','FAILED')
    )
);

CREATE INDEX idx_payment_reconciliation_items_session
    ON payment_reconciliation_items(session_id, row_number);
CREATE INDEX idx_payment_reconciliation_items_resolution
    ON payment_reconciliation_items(session_id, resolution_status);
CREATE INDEX idx_payment_reconciliation_items_match_status
    ON payment_reconciliation_items(match_status);
CREATE INDEX idx_payment_reconciliation_items_matched_payment
    ON payment_reconciliation_items(matched_payment_id)
    WHERE matched_payment_id IS NOT NULL;
