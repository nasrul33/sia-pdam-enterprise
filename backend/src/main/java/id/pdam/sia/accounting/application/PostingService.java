package id.pdam.sia.accounting.application;

import id.pdam.sia.accounting.domain.AccountingPeriod;
import id.pdam.sia.accounting.domain.JournalEntry;
import id.pdam.sia.reporting.application.LedgerEntryMaterializationService;
import id.pdam.sia.shared.audit.AuditTrailService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostingService {
    private final AuditTrailService auditTrailService;
    private final LedgerEntryMaterializationService ledgerEntryMaterializationService;

    public PostingService(
            AuditTrailService auditTrailService,
            LedgerEntryMaterializationService ledgerEntryMaterializationService
    ) {
        this.auditTrailService = auditTrailService;
        this.ledgerEntryMaterializationService = ledgerEntryMaterializationService;
    }

    @Transactional
    public void post(JournalEntry journalEntry, AccountingPeriod period, String actor, String reason) {
        journalEntry.post(period, actor);
        ledgerEntryMaterializationService.materializePostedJournal(journalEntry);
        auditTrailService.record(actor, "ACCOUNTING", "POST_JOURNAL", String.valueOf(journalEntry.getId()), reason);
    }
}
