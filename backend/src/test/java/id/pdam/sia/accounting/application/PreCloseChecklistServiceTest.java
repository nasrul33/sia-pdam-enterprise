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
import id.pdam.sia.receivable.domain.ReceivableAgingSnapshot;
import id.pdam.sia.receivable.repository.ReceivableAgingSnapshotRepository;
import id.pdam.sia.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PreCloseChecklistServiceTest {
    private final JournalEntryRepository journalEntryRepository = mock(JournalEntryRepository.class);
    private final FixedAssetRepository fixedAssetRepository = mock(FixedAssetRepository.class);
    private final PaymentReconciliationSessionRepository reconciliationSessionRepository =
            mock(PaymentReconciliationSessionRepository.class);
    private final BillingBatchRepository billingBatchRepository = mock(BillingBatchRepository.class);
    private final InvoiceRepository invoiceRepository = mock(InvoiceRepository.class);
    private final ReceivableAgingSnapshotRepository agingSnapshotRepository =
            mock(ReceivableAgingSnapshotRepository.class);

    private final PreCloseChecklistService service = new PreCloseChecklistService(
            journalEntryRepository,
            fixedAssetRepository,
            reconciliationSessionRepository,
            billingBatchRepository,
            invoiceRepository,
            agingSnapshotRepository
    );

    @Test
    void checklistReturnsStableBlockerCodesCountsAndActions() {
        AccountingPeriod period = new AccountingPeriod("2026-07");
        when(journalEntryRepository.countByAccountingPeriodIdAndStatus(period.getId(), JournalStatus.DRAFT))
                .thenReturn(2L);
        when(fixedAssetRepository.countMissingDepreciationForPeriod(
                "2026-07",
                LocalDate.of(2026, 7, 31),
                Instant.parse("2026-07-31T17:00:00Z"),
                FixedAssetStatus.ACTIVE,
                FixedAssetStatus.DISPOSED
        )).thenReturn(2L);
        when(reconciliationSessionRepository.countUnfinishedForPeriod(any(), any(), any(), any())).thenReturn(1L);
        when(billingBatchRepository.countByPeriodAndStatusNotIn(
                "2026-07",
                List.of(BillingBatchStatus.COMPLETED, BillingBatchStatus.VOID)
        )).thenReturn(1L);
        when(invoiceRepository.countByPeriodAndStatus("2026-07", InvoiceStatus.DRAFT)).thenReturn(4L);
        when(agingSnapshotRepository.findByPeriod("2026-07"))
                .thenReturn(Optional.of(mock(ReceivableAgingSnapshot.class)));

        PreCloseChecklist result = service.evaluate(period);

        assertThat(result.closable()).isFalse();
        assertThat(result.blockers()).extracting(PreCloseBlocker::code).containsExactly(
                "DRAFT_JOURNALS",
                "MISSING_ASSET_DEPRECIATION",
                "INCOMPLETE_PAYMENT_RECONCILIATIONS",
                "UNFINISHED_BILLING_BATCHES",
                "DRAFT_INVOICES",
                "MISSING_RECEIVABLE_ALLOWANCE"
        );
        assertThat(result.blockers()).extracting(PreCloseBlocker::count).containsExactly(2L, 2L, 1L, 1L, 4L, 1L);
        assertThat(result.blockers()).allSatisfy(blocker -> {
            assertThat(blocker.severity()).isEqualTo(PreCloseSeverity.BLOCKER);
            assertThat(blocker.message()).isNotBlank();
            assertThat(blocker.actionPath()).startsWith("/");
        });
        verify(reconciliationSessionRepository).countUnfinishedForPeriod(
                Instant.parse("2026-06-30T17:00:00Z"),
                Instant.parse("2026-07-31T17:00:00Z"),
                PaymentReconciliationSessionStatus.CANCELLED,
                PaymentReconciliationSessionStatus.COMPLETED
        );
    }

    @Test
    void checklistIsClearWhenNoWorkRemainsAndNoAgingSnapshotExists() {
        AccountingPeriod period = new AccountingPeriod("2026-07");
        when(agingSnapshotRepository.findByPeriod("2026-07")).thenReturn(Optional.empty());

        PreCloseChecklist result = service.evaluate(period);

        assertThat(result.closable()).isTrue();
        assertThat(result.blockers()).isEmpty();
        assertThatCode(() -> service.requireClear(period)).doesNotThrowAnyException();
    }

    @Test
    void fullyDepreciatedAssetDoesNotBlockWhenSingleEligibilityQueryReturnsZero() {
        AccountingPeriod period = new AccountingPeriod("2026-07");
        when(fixedAssetRepository.countMissingDepreciationForPeriod(
                "2026-07",
                LocalDate.of(2026, 7, 31),
                Instant.parse("2026-07-31T17:00:00Z"),
                FixedAssetStatus.ACTIVE,
                FixedAssetStatus.DISPOSED
        )).thenReturn(0L);

        PreCloseChecklist result = service.evaluate(period);

        assertThat(result.blockers()).extracting(PreCloseBlocker::code)
                .doesNotContain("MISSING_ASSET_DEPRECIATION");
    }

    @Test
    void eligibleDisposedAfterPeriodAssetBlocksWhenDepreciationIsMissing() {
        AccountingPeriod period = new AccountingPeriod("2026-07");
        when(fixedAssetRepository.countMissingDepreciationForPeriod(
                "2026-07",
                LocalDate.of(2026, 7, 31),
                Instant.parse("2026-07-31T17:00:00Z"),
                FixedAssetStatus.ACTIVE,
                FixedAssetStatus.DISPOSED
        )).thenReturn(1L);

        PreCloseChecklist result = service.evaluate(period);

        assertThat(result.blockers()).filteredOn(blocker -> blocker.code().equals("MISSING_ASSET_DEPRECIATION"))
                .singleElement()
                .extracting(PreCloseBlocker::count)
                .isEqualTo(1L);
    }

    @Test
    void existingDepreciationDoesNotBlockWhenSingleEligibilityQueryReturnsZero() {
        AccountingPeriod period = new AccountingPeriod("2026-07");
        when(fixedAssetRepository.countMissingDepreciationForPeriod(
                "2026-07",
                LocalDate.of(2026, 7, 31),
                Instant.parse("2026-07-31T17:00:00Z"),
                FixedAssetStatus.ACTIVE,
                FixedAssetStatus.DISPOSED
        )).thenReturn(0L);

        PreCloseChecklist result = service.evaluate(period);

        assertThat(result.blockers()).extracting(PreCloseBlocker::code)
                .doesNotContain("MISSING_ASSET_DEPRECIATION");
    }

    @Test
    void freshAllowanceJournalClearsAllowanceBlocker() {
        AccountingPeriod period = new AccountingPeriod("2026-07");
        Instant generatedAt = Instant.parse("2026-08-01T01:00:00Z");
        ReceivableAgingSnapshot snapshot = mock(ReceivableAgingSnapshot.class);
        when(snapshot.getGeneratedAt()).thenReturn(generatedAt);
        when(agingSnapshotRepository.findByPeriod("2026-07")).thenReturn(Optional.of(snapshot));
        when(journalEntryRepository
                .countByAccountingPeriodIdAndSourceModuleAndSourceDocumentNumberAndStatusAndPostedAtGreaterThanEqual(
                        period.getId(),
                        "RECEIVABLE_ALLOWANCE",
                        "2026-07",
                        JournalStatus.POSTED,
                        generatedAt
                )).thenReturn(1L);

        PreCloseChecklist result = service.evaluate(period);

        assertThat(result.blockers()).extracting(PreCloseBlocker::code)
                .doesNotContain("MISSING_RECEIVABLE_ALLOWANCE");
    }

    @Test
    void staleAllowanceJournalKeepsAllowanceBlocker() {
        AccountingPeriod period = new AccountingPeriod("2026-07");
        Instant generatedAt = Instant.parse("2026-08-01T01:00:00Z");
        ReceivableAgingSnapshot snapshot = mock(ReceivableAgingSnapshot.class);
        when(snapshot.getGeneratedAt()).thenReturn(generatedAt);
        when(agingSnapshotRepository.findByPeriod("2026-07")).thenReturn(Optional.of(snapshot));

        PreCloseChecklist result = service.evaluate(period);

        assertThat(result.blockers()).filteredOn(blocker -> blocker.code().equals("MISSING_RECEIVABLE_ALLOWANCE"))
                .singleElement()
                .extracting(PreCloseBlocker::count)
                .isEqualTo(1L);
    }

    @Test
    void requireClearRejectsAnyBlockerWithStableBusinessError() {
        AccountingPeriod period = new AccountingPeriod("2026-07");
        when(journalEntryRepository.countByAccountingPeriodIdAndStatus(period.getId(), JournalStatus.DRAFT))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.requireClear(period))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).code())
                        .isEqualTo("PERIOD_PRE_CLOSE_BLOCKED"));
    }
}
