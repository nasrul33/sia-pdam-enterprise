ALTER TABLE invoices
    ADD COLUMN void_journal_entry_id UUID REFERENCES journal_entries(id),
    ADD COLUMN voided_at TIMESTAMPTZ;

CREATE UNIQUE INDEX uq_invoices_void_journal_entry
    ON invoices(void_journal_entry_id)
    WHERE void_journal_entry_id IS NOT NULL;

ALTER TABLE invoices
    ADD CONSTRAINT chk_invoices_void_trace CHECK (
        status <> 'VOID'
        OR (void_journal_entry_id IS NOT NULL AND voided_at IS NOT NULL)
    );
