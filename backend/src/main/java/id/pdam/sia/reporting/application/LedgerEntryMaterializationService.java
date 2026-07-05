package id.pdam.sia.reporting.application;

import id.pdam.sia.accounting.domain.JournalEntry;
import id.pdam.sia.accounting.domain.JournalLine;
import id.pdam.sia.accounting.domain.JournalStatus;
import id.pdam.sia.reporting.domain.LedgerEntry;
import id.pdam.sia.reporting.repository.LedgerEntryRepository;
import id.pdam.sia.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;

@Service
public class LedgerEntryMaterializationService {
    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerEntryMaterializationService(LedgerEntryRepository ledgerEntryRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional
    public void materializePostedJournal(JournalEntry journalEntry) {
        if (journalEntry == null) {
            throw new BusinessException("LEDGER_JOURNAL_REQUIRED", "Posted journal is required for ledger materialization.");
        }
        if (journalEntry.getStatus() != JournalStatus.POSTED || journalEntry.getPostedAt() == null) {
            throw new BusinessException("LEDGER_JOURNAL_NOT_POSTED", "Only posted journal can be materialized to ledger.");
        }
        if (ledgerEntryRepository.existsByJournalEntryId(journalEntry.getId())) {
            throw new BusinessException("LEDGER_JOURNAL_ALREADY_MATERIALIZED", "Posted journal was already materialized to ledger.");
        }

        LocalDate postingDate = LocalDate.ofInstant(journalEntry.getPostedAt(), ZoneOffset.UTC);
        for (JournalLine line : journalEntry.getLines()) {
            ledgerEntryRepository.save(new LedgerEntry(
                    journalEntry.getId(),
                    line.getId(),
                    line.getAccountId(),
                    postingDate,
                    line.getDebit(),
                    line.getCredit(),
                    "ACCOUNTING",
                    journalEntry.getId()
            ));
        }
    }
}
