ALTER TABLE payment_reconciliation_items
    ADD COLUMN adjustment_journal_entry_id UUID,
    ADD COLUMN adjustment_reason TEXT,
    ADD COLUMN adjusted_by VARCHAR(128),
    ADD COLUMN adjusted_at TIMESTAMPTZ;

CREATE UNIQUE INDEX uq_payment_reconciliation_items_adjustment_journal
    ON payment_reconciliation_items(adjustment_journal_entry_id)
    WHERE adjustment_journal_entry_id IS NOT NULL;

CREATE INDEX idx_payment_reconciliation_items_adjusted_at
    ON payment_reconciliation_items(adjusted_at)
    WHERE adjusted_at IS NOT NULL;
