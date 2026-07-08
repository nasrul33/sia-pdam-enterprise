CREATE TABLE payment_reconciliation_handoff_notes (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES payment_reconciliation_sessions(id),
    note_text TEXT NOT NULL,
    handoff_owner VARCHAR(128),
    handoff_due_date DATE,
    handoff_status VARCHAR(32) NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    updated_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_payment_reconciliation_handoff_notes_status CHECK (handoff_status IN ('OPEN','IN_PROGRESS','CLEARED')),
    CONSTRAINT chk_payment_reconciliation_handoff_notes_text CHECK (
        length(btrim(note_text)) > 0
        AND char_length(note_text) <= 2000
    )
);

CREATE INDEX idx_payment_reconciliation_handoff_notes_session_updated
    ON payment_reconciliation_handoff_notes(session_id, updated_at DESC);

CREATE INDEX idx_payment_reconciliation_handoff_notes_status_due
    ON payment_reconciliation_handoff_notes(handoff_status, handoff_due_date)
    WHERE handoff_due_date IS NOT NULL;

CREATE TABLE payment_reconciliation_handoff_note_revisions (
    id UUID PRIMARY KEY,
    note_id UUID NOT NULL REFERENCES payment_reconciliation_handoff_notes(id),
    revision_number INTEGER NOT NULL,
    note_text TEXT NOT NULL,
    handoff_owner VARCHAR(128),
    handoff_due_date DATE,
    handoff_status VARCHAR(32) NOT NULL,
    reason TEXT NOT NULL,
    changed_by VARCHAR(128) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_payment_reconciliation_handoff_note_revision UNIQUE (note_id, revision_number),
    CONSTRAINT chk_payment_reconciliation_handoff_note_revisions_number CHECK (revision_number > 0),
    CONSTRAINT chk_payment_reconciliation_handoff_note_revisions_status CHECK (handoff_status IN ('OPEN','IN_PROGRESS','CLEARED')),
    CONSTRAINT chk_payment_reconciliation_handoff_note_revisions_text CHECK (
        length(btrim(note_text)) > 0
        AND char_length(note_text) <= 2000
    ),
    CONSTRAINT chk_payment_reconciliation_handoff_note_revisions_reason CHECK (
        length(btrim(reason)) > 0
        AND char_length(reason) <= 500
    )
);

CREATE INDEX idx_payment_reconciliation_handoff_note_revisions_note
    ON payment_reconciliation_handoff_note_revisions(note_id, revision_number DESC);
