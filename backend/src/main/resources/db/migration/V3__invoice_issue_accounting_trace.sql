ALTER TABLE journal_entries
    ADD COLUMN source_module VARCHAR(64),
    ADD COLUMN source_record_id UUID,
    ADD COLUMN source_document_number VARCHAR(64);

CREATE UNIQUE INDEX uq_journal_entries_source
    ON journal_entries(source_module, source_record_id)
    WHERE source_module IS NOT NULL AND source_record_id IS NOT NULL;

ALTER TABLE invoices
    ADD COLUMN issue_journal_entry_id UUID REFERENCES journal_entries(id);

CREATE UNIQUE INDEX uq_invoices_issue_journal_entry
    ON invoices(issue_journal_entry_id)
    WHERE issue_journal_entry_id IS NOT NULL;
