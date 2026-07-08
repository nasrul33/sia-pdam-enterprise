ALTER TABLE payment_reconciliation_sessions
    ADD COLUMN signed_off_by VARCHAR(128),
    ADD COLUMN signed_off_at TIMESTAMPTZ,
    ADD COLUMN sign_off_reason TEXT;

ALTER TABLE payment_reconciliation_sessions
    ADD CONSTRAINT chk_payment_reconciliation_sessions_sign_off_completed CHECK (
        (
            signed_off_by IS NULL
            AND signed_off_at IS NULL
            AND sign_off_reason IS NULL
        )
        OR (
            status = 'COMPLETED'
            AND signed_off_by IS NOT NULL
            AND signed_off_at IS NOT NULL
            AND sign_off_reason IS NOT NULL
        )
    );

CREATE INDEX idx_payment_reconciliation_sessions_signed_off_at
    ON payment_reconciliation_sessions(signed_off_at)
    WHERE signed_off_at IS NOT NULL;
