package id.pdam.sia.payment.application;

import id.pdam.sia.payment.domain.Payment;
import id.pdam.sia.payment.domain.PaymentStatus;
import id.pdam.sia.payment.repository.PaymentRepository;
import id.pdam.sia.shared.audit.AuditTrailService;
import id.pdam.sia.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentReconciliationApplicationServiceTest {
    private static final Instant PAID_AT = Instant.parse("2026-07-31T12:00:00Z");

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final AuditTrailService auditTrailService = mock(AuditTrailService.class);
    private final PaymentReconciliationApplicationService service = new PaymentReconciliationApplicationService(
            paymentRepository,
            auditTrailService
    );

    @Test
    void exportsReconcilablePaymentsWithBoundedPageAndAuditTrail() {
        Payment payment = settledPayment("PAY-20260731-0001", "BANK-0001", new BigDecimal("100000.00"));
        when(paymentRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Payment>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(payment)));

        List<PaymentReconciliationExportRow> rows = service.exportPayments(
                new PaymentReconciliationFilters(PaymentStatus.SETTLED, " counter ", PAID_AT.minusSeconds(3600), PAID_AT.plusSeconds(3600)),
                "finance.supervisor"
        );

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).paymentNumber()).isEqualTo("PAY-20260731-0001");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(paymentRepository).findAll(org.mockito.ArgumentMatchers.<Specification<Payment>>any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10_000);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("paidAt")).isNotNull();
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("paidAt").isDescending()).isTrue();
        verify(auditTrailService).record(
                eq("finance.supervisor"),
                eq("PAYMENT"),
                eq("EXPORT_RECONCILIATION"),
                eq("payment-reconciliation"),
                org.mockito.ArgumentMatchers.contains("rows=1")
        );
    }

    @Test
    void rejectsPendingOrFailedPaymentExportForReconciliation() {
        assertThatThrownBy(() -> service.exportPayments(
                new PaymentReconciliationFilters(PaymentStatus.PENDING, null, null, null),
                "finance.supervisor"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only settled or reversed payments can be exported");

        verify(paymentRepository, never()).findAll(org.mockito.ArgumentMatchers.<Specification<Payment>>any(), any(Pageable.class));
        verify(auditTrailService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void matchesExactReferenceAndReportsAmountVariance() {
        Payment exactPayment = settledPayment("PAY-20260731-0001", "BANK-0001", new BigDecimal("100000.00"));
        Payment variancePayment = settledPayment("PAY-20260731-0002", "BANK-0002", new BigDecimal("90000.00"));

        when(paymentRepository.findByPaymentNumber("PAY-20260731-0001")).thenReturn(Optional.of(exactPayment));
        when(paymentRepository.findByPaymentNumber("BANK-0002")).thenReturn(Optional.empty());
        when(paymentRepository.findByExternalReference("BANK-0002")).thenReturn(Optional.of(variancePayment));

        PaymentReconciliationMatchReport report = service.matchBankStatementRows(
                List.of(
                        new BankStatementRowCommand("PAY-20260731-0001", new BigDecimal("100000.00"), PAID_AT, "counter"),
                        new BankStatementRowCommand("BANK-0002", new BigDecimal("95000.00"), PAID_AT, "counter")
                ),
                "finance.supervisor"
        );

        assertThat(report.summary().totalRows()).isEqualTo(2);
        assertThat(report.summary().exactMatches()).isEqualTo(1);
        assertThat(report.summary().amountVariances()).isEqualTo(1);
        assertThat(report.summary().totalVariance()).isEqualByComparingTo("5000.00");
        assertThat(report.matches().get(0).status()).isEqualTo(PaymentReconciliationMatchStatus.EXACT_MATCH);
        assertThat(report.matches().get(1).status()).isEqualTo(PaymentReconciliationMatchStatus.AMOUNT_VARIANCE);
        assertThat(report.matches().get(1).matchedPaymentNumber()).isEqualTo("PAY-20260731-0002");
        verify(auditTrailService).record(
                "finance.supervisor",
                "PAYMENT",
                "MATCH_BANK_STATEMENT",
                "payment-reconciliation",
                "rows=2"
        );
    }

    @Test
    void flagsReversedPaymentsAndMultipleCandidatesWithoutPostingJournalMutation() {
        Payment reversedPayment = settledPayment("PAY-20260731-0003", "BANK-0003", new BigDecimal("50000.00"));
        reversedPayment.linkSettlementJournal(java.util.UUID.randomUUID());
        reversedPayment.reverse(PAID_AT.plusSeconds(60), "koreksi bayar", java.util.UUID.randomUUID());
        Payment candidateA = settledPayment("PAY-20260731-0004", "BANK-0004", new BigDecimal("75000.00"));
        Payment candidateB = settledPayment("PAY-20260731-0005", "BANK-0005", new BigDecimal("75000.00"));

        when(paymentRepository.findByPaymentNumber("BANK-0003")).thenReturn(Optional.empty());
        when(paymentRepository.findByExternalReference("BANK-0003")).thenReturn(Optional.of(reversedPayment));
        when(paymentRepository.findByPaymentNumber("NOREF-75000")).thenReturn(Optional.empty());
        when(paymentRepository.findByExternalReference("NOREF-75000")).thenReturn(Optional.empty());
        when(paymentRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Payment>>any(), any(Sort.class)))
                .thenReturn(List.of(candidateA, candidateB));

        PaymentReconciliationMatchReport report = service.matchBankStatementRows(
                List.of(
                        new BankStatementRowCommand("BANK-0003", new BigDecimal("50000.00"), PAID_AT, "counter"),
                        new BankStatementRowCommand("NOREF-75000", new BigDecimal("75000.00"), PAID_AT, "counter")
                ),
                "auditor.internal"
        );

        assertThat(report.summary().reversedPayments()).isEqualTo(1);
        assertThat(report.summary().multipleCandidates()).isEqualTo(1);
        assertThat(report.matches().get(0).status()).isEqualTo(PaymentReconciliationMatchStatus.REVERSED_PAYMENT);
        assertThat(report.matches().get(1).status()).isEqualTo(PaymentReconciliationMatchStatus.MULTIPLE_CANDIDATES);
        assertThat(report.matches().get(1).matchedPaymentId()).isNull();
    }

    private static Payment settledPayment(String paymentNumber, String externalReference, BigDecimal amount) {
        Payment payment = new Payment(
                paymentNumber,
                paymentNumber.toLowerCase(),
                "COUNTER",
                externalReference,
                amount,
                PAID_AT
        );
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SETTLED);
        return payment;
    }
}
