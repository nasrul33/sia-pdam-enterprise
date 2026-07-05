ALTER TABLE payments
    ADD COLUMN reversal_journal_entry_id UUID REFERENCES journal_entries(id);

CREATE UNIQUE INDEX uq_payments_reversal_journal_entry
    ON payments(reversal_journal_entry_id)
    WHERE reversal_journal_entry_id IS NOT NULL;
