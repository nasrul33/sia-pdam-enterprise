package id.pdam.sia.accounting.application;

import id.pdam.sia.accounting.domain.AccountingPeriod;
import id.pdam.sia.accounting.domain.FixedAssetStatus;
import id.pdam.sia.accounting.domain.JournalStatus;
import id.pdam.sia.accounting.repository.FixedAssetDepreciationRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PreCloseChecklistServiceTest {
    private final JournalEntryRepository journalEntryRepository = mock(JournalEntryRepository.class);
    private final FixedAssetRepository fixedAssetRepository = mock(FixedAssetRepository.class);
    private final FixedAssetDepreciationRepository fixedAssetDepreciationRepository =
            mock(FixedAssetDepreciationRepository.class);
    private final PaymentReconciliationSessionRepository reconciliationSessionRepository =
            mock(PaymentReconciliationSessionRepository.class);
    private final BillingBatchRepository billingBatchRepository = mock(BillingBatchRepository.class);
    private final InvoiceRepository invoiceRepository = mock(InvoiceRepository.class);
    private final ReceivableAgingSnapshotRepository agingSnapshotRepository =
            mock(ReceivableAgingSnapshotRepository.class);

    private final PreCloseChecklistService service = new PreCloseChecklistService(
            journalEntryRepository,
            fixedAssetRepository,
            fixedAssetDepreciationRepository,
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
        when(fixedAssetRepository.countByStatusAndAcquisitionDateLessThanEqual(
                FixedAssetStatus.ACTIVE,
                LocalDate.of(2026, 7, 31)
        )).thenReturn(3L);
        when(fixedAssetDepreciationRepository.countForActiveAssetsByPeriod(
                "2026-07",
                FixedAssetStatus.ACTIVE,
                LocalDate.of(2026, 7, 31)
        )).thenReturn(1L);
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
        when(fixedAssetRepository.countByStatusAndAcquisitionDateLessThanEqual(any(), any())).thenReturn(2L);
        when(fixedAssetDepreciationRepository.countForActiveAssetsByPeriod(anyString(), any(), any()))
                .thenReturn(2L);
        when(agingSnapshotRepository.findByPeriod("2026-07")).thenReturn(Optional.empty());

        PreCloseChecklist result = service.evaluate(period);

        assertThat(result.closable()).isTrue();
        assertThat(result.blockers()).isEmpty();
        assertThatCode(() -> service.requireClear(period)).doesNotThrowAnyException();
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
