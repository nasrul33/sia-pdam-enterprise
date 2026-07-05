package id.pdam.sia.accounting.application;

import id.pdam.sia.accounting.domain.AccountingPeriod;
import id.pdam.sia.accounting.domain.JournalEntry;
import id.pdam.sia.shared.audit.AuditTrailService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostingService {
    private final AuditTrailService auditTrailService;

    public PostingService(AuditTrailService auditTrailService) {
        this.auditTrailService = auditTrailService;
    }

    @Transactional
    public void post(JournalEntry journalEntry, AccountingPeriod period, String actor) {
        journalEntry.post(period, actor);
        auditTrailService.record(actor, "ACCOUNTING", "POST_JOURNAL", String.valueOf(journalEntry.getId()), "journal posting");
    }
}
