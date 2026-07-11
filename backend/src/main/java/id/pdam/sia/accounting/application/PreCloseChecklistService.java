package id.pdam.sia.accounting.application;

import id.pdam.sia.accounting.domain.AccountingPeriod;
import id.pdam.sia.accounting.domain.FixedAssetStatus;
import id.pdam.sia.accounting.domain.JournalStatus;
import id.pdam.sia.accounting.repository.FixedAssetRepository;
import id.pdam.sia.accounting.repository.JournalEntryRepository;
import id.pdam.sia.billing.domain.BillingBatchStatus;
import id.pdam.sia.billing.domain.InvoiceStatus;
import id.pdam.sia.billing.repository.BillingBatchRepository;
import id.pdam.sia.billing.repository.InvoiceRepository;
import id.pdam.sia.payment.domain.PaymentReconciliationSessionStatus;
import id.pdam.sia.payment.repository.PaymentReconciliationSessionRepository;
import id.pdam.sia.receivable.repository.ReceivableAgingSnapshotRepository;
import id.pdam.sia.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class PreCloseChecklistService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Jakarta");
    private static final String ALLOWANCE_SOURCE_MODULE = "RECEIVABLE_ALLOWANCE";
    private static final List<BillingBatchStatus> FINISHED_BILLING_BATCH_STATUSES =
            List.of(BillingBatchStatus.COMPLETED, BillingBatchStatus.VOID);

    private final JournalEntryRepository journalEntryRepository;
    private final FixedAssetRepository fixedAssetRepository;
    private final PaymentReconciliationSessionRepository reconciliationSessionRepository;
    private final BillingBatchRepository billingBatchRepository;
    private final InvoiceRepository invoiceRepository;
    private final ReceivableAgingSnapshotRepository agingSnapshotRepository;

    public PreCloseChecklistService(
            JournalEntryRepository journalEntryRepository,
            FixedAssetRepository fixedAssetRepository,
            PaymentReconciliationSessionRepository reconciliationSessionRepository,
            BillingBatchRepository billingBatchRepository,
            InvoiceRepository invoiceRepository,
            ReceivableAgingSnapshotRepository agingSnapshotRepository
    ) {
        this.journalEntryRepository = journalEntryRepository;
        this.fixedAssetRepository = fixedAssetRepository;
        this.reconciliationSessionRepository = reconciliationSessionRepository;
        this.billingBatchRepository = billingBatchRepository;
        this.invoiceRepository = invoiceRepository;
        this.agingSnapshotRepository = agingSnapshotRepository;
    }

    @Transactional(readOnly = true)
    public PreCloseChecklist evaluate(AccountingPeriod period) {
        YearMonth yearMonth = YearMonth.parse(period.getPeriod());
        LocalDate periodEnd = yearMonth.atEndOfMonth();
        Instant periodEndExclusive = yearMonth.plusMonths(1).atDay(1).atStartOfDay(BUSINESS_ZONE).toInstant();
        List<PreCloseBlocker> blockers = new ArrayList<>();

        addBlocker(
                blockers,
                "DRAFT_JOURNALS",
                "Draft journals must be posted or voided before closing review.",
                journalEntryRepository.countByAccountingPeriodIdAndStatus(period.getId(), JournalStatus.DRAFT),
                "/accounting?status=DRAFT&accountingPeriodId=" + period.getId()
        );

        long missingDepreciation = fixedAssetRepository.countMissingDepreciationForPeriod(
                period.getPeriod(),
                periodEnd,
                periodEndExclusive,
                FixedAssetStatus.ACTIVE,
                FixedAssetStatus.DISPOSED
        );
        addBlocker(
                blockers,
                "MISSING_ASSET_DEPRECIATION",
                "Active assets must have depreciation posted for the accounting period.",
                missingDepreciation,
                "/accounting/assets?status=ACTIVE&period=" + period.getPeriod()
        );

        addBlocker(
                blockers,
                "INCOMPLETE_PAYMENT_RECONCILIATIONS",
                "Payment reconciliation sessions must be completed and signed off.",
                reconciliationSessionRepository.countUnfinishedForPeriod(
                        yearMonth.atDay(1).atStartOfDay(BUSINESS_ZONE).toInstant(),
                        yearMonth.plusMonths(1).atDay(1).atStartOfDay(BUSINESS_ZONE).toInstant(),
                        PaymentReconciliationSessionStatus.CANCELLED,
                        PaymentReconciliationSessionStatus.COMPLETED
                ),
                "/payments?view=reconciliation&period=" + period.getPeriod()
        );

        addBlocker(
                blockers,
                "UNFINISHED_BILLING_BATCHES",
                "Billing batches must be completed or voided before closing review.",
                billingBatchRepository.countByPeriodAndStatusNotIn(
                        period.getPeriod(),
                        FINISHED_BILLING_BATCH_STATUSES
                ),
                "/billing?period=" + period.getPeriod()
        );

        addBlocker(
                blockers,
                "DRAFT_INVOICES",
                "Draft invoices must be issued or voided before closing review.",
                invoiceRepository.countByPeriodAndStatus(period.getPeriod(), InvoiceStatus.DRAFT),
                "/billing?period=" + period.getPeriod() + "&invoiceStatus=DRAFT"
        );

        agingSnapshotRepository.findByPeriod(period.getPeriod()).ifPresent(snapshot -> {
            long allowanceJournals = journalEntryRepository
                    .countByAccountingPeriodIdAndSourceModuleAndSourceDocumentNumberAndStatusAndPostedAtGreaterThanEqual(
                            period.getId(),
                            ALLOWANCE_SOURCE_MODULE,
                            period.getPeriod(),
                            JournalStatus.POSTED,
                            snapshot.getGeneratedAt()
                    );
            addBlocker(
                    blockers,
                    "MISSING_RECEIVABLE_ALLOWANCE",
                    "A posted receivable allowance journal is required for the aging snapshot.",
                    allowanceJournals == 0L ? 1L : 0L,
                    "/receivables/aging?period=" + period.getPeriod()
            );
        });

        return new PreCloseChecklist(period.getId(), period.getPeriod(), blockers.isEmpty(), blockers);
    }

    @Transactional(readOnly = true)
    public void requireClear(AccountingPeriod period) {
        if (!evaluate(period).closable()) {
            throw new BusinessException(
                    "PERIOD_PRE_CLOSE_BLOCKED",
                    "Accounting period cannot enter closing review while pre-close blockers exist."
            );
        }
    }

    private static void addBlocker(
            List<PreCloseBlocker> blockers,
            String code,
            String message,
            long count,
            String actionPath
    ) {
        if (count > 0L) {
            blockers.add(new PreCloseBlocker(code, message, count, PreCloseSeverity.BLOCKER, actionPath));
        }
    }
}
