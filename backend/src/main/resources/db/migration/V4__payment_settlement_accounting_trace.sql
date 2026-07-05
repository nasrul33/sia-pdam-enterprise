ALTER TABLE payments
    ADD COLUMN settlement_journal_entry_id UUID REFERENCES journal_entries(id);

CREATE UNIQUE INDEX uq_payments_settlement_journal_entry
    ON payments(settlement_journal_entry_id)
    WHERE settlement_journal_entry_id IS NOT NULL;
