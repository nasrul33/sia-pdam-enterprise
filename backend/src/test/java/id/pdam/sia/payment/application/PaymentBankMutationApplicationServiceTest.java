package id.pdam.sia.payment.application;

import id.pdam.sia.payment.domain.BankMutation;
import id.pdam.sia.payment.domain.BankMutationStatus;
import id.pdam.sia.payment.domain.Payment;
import id.pdam.sia.payment.domain.PaymentReconciliationItem;
import id.pdam.sia.payment.domain.PaymentReconciliationSession;
import id.pdam.sia.payment.repository.BankMutationRepository;
import id.pdam.sia.shared.audit.AuditTrailService;
import id.pdam.sia.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentBankMutationApplicationServiceTest {
    private static final Instant MUTATION_AT = Instant.parse("2026-07-31T03:15:00Z");

    private final BankMutationRepository bankMutationRepository = mock(BankMutationRepository.class);
    private final PaymentReconciliationApplicationService reconciliationApplicationService =
            mock(PaymentReconciliationApplicationService.class);
    private final AuditTrailService auditTrailService = mock(AuditTrailService.class);
    private final PaymentBankMutationApplicationService service = new PaymentBankMutationApplicationService(
            bankMutationRepository,
            reconciliationApplicationService,
            auditTrailService
    );

    @Test
    void importsBankMutationsWithDuplicateReferenceGuardAndAuditTrail() {
        when(bankMutationRepository.existsByExternalReference("BANK-001")).thenReturn(false);
        when(bankMutationRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentBankMutationApplicationService.BankMutationImportResult result = service.importMutations(
                new PaymentBankMutationApplicationService.ImportBankMutationsCommand(
                        " statement.csv ",
                        " bank-operasional ",
                        List.of(new PaymentBankMutationApplicationService.BankMutationRowCommand(
                                "BANK-001",
                                new BigDecimal("150000.00"),
                                MUTATION_AT,
                                " counter ",
                                "payment receipt"
                        ))
                ),
                "finance.cash"
        );

        assertThat(result.importedRows()).isEqualTo(1);
        assertThat(result.mutations().get(0).getExternalReference()).isEqualTo("BANK-001");
        assertThat(result.mutations().get(0).getSourceFilename()).isEqualTo("statement.csv");
        assertThat(result.mutations().get(0).getBankAccountReference()).isEqualTo("bank-operasional");
        assertThat(result.mutations().get(0).getChannel()).isEqualTo("COUNTER");
        assertThat(result.mutations().get(0).getStatus()).isEqualTo(BankMutationStatus.UNMATCHED);
        verify(auditTrailService).record("finance.cash", "PAYMENT", "IMPORT_BANK_MUTATIONS", "statement.csv", "rows=1");

        when(bankMutationRepository.existsByExternalReference("BANK-DUP")).thenReturn(true);

        assertThatThrownBy(() -> service.importMutations(
                new PaymentBankMutationApplicationService.ImportBankMutationsCommand(
                        "statement.csv",
                        "bank-operasional",
                        List.of(new PaymentBankMutationApplicationService.BankMutationRowCommand(
                                "BANK-DUP",
                                new BigDecimal("1000.00"),
                                MUTATION_AT,
                                "counter",
                                null
                        ))
                ),
                "finance.cash"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void reconcileDailyCreatesSessionAndMarksExactMatchOnly() {
        LocalDate date = LocalDate.of(2026, 7, 31);
        BankMutation exactMutation = new BankMutation(
                "PAY-20260731-0001",
                "statement.csv",
                "bank-operasional",
                new BigDecimal("125000.00"),
                MUTATION_AT,
                "counter",
                "exact payment"
        );
        BankMutation exceptionMutation = new BankMutation(
                "BANK-UNMATCHED",
                "statement.csv",
                "bank-operasional",
                new BigDecimal("50000.00"),
                MUTATION_AT.plusSeconds(60),
                "counter",
                "manual review"
        );
        Payment payment = new Payment(
                "PAY-20260731-0001",
                "idem-001",
                "COUNTER",
                "PAY-20260731-0001",
                new BigDecimal("125000.00"),
                MUTATION_AT
        );
        PaymentReconciliationMatchResult exactMatch = PaymentReconciliationMatchResult.matched(
                1,
                new BankStatementRowCommand("PAY-20260731-0001", new BigDecimal("125000.00"), MUTATION_AT, "COUNTER"),
                payment,
                PaymentReconciliationMatchStatus.EXACT_MATCH,
                BigDecimal.ZERO.setScale(2),
                "Exact match."
        );
        PaymentReconciliationMatchResult unmatched = PaymentReconciliationMatchResult.unmatched(
                2,
                new BankStatementRowCommand("BANK-UNMATCHED", new BigDecimal("50000.00"), MUTATION_AT.plusSeconds(60), "COUNTER")
        );
        PaymentReconciliationSession session = new PaymentReconciliationSession(
                "REC-20260731-0001",
                "statement.csv",
                "bank-operasional",
                "finance.cash",
                PaymentReconciliationMatchSummary.from(List.of(exactMatch, unmatched))
        );
        PaymentReconciliationItem exactItem = PaymentReconciliationItem.from(session.getId(), exactMatch);
        PaymentReconciliationItem unmatchedItem = PaymentReconciliationItem.from(session.getId(), unmatched);

        when(bankMutationRepository.findByStatusAndTransactedAtGreaterThanEqualAndTransactedAtLessThanOrderByTransactedAtAsc(
                BankMutationStatus.UNMATCHED,
                date.atStartOfDay().toInstant(java.time.ZoneOffset.UTC),
                date.plusDays(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC)
        )).thenReturn(List.of(exactMutation, exceptionMutation));
        when(reconciliationApplicationService.createSession(any(), any(), any(), any()))
                .thenReturn(new PaymentReconciliationSessionResult(session, List.of(exactItem, unmatchedItem)));
        when(bankMutationRepository.save(any(BankMutation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentBankMutationApplicationService.BankMutationReconciliationResult result = service.reconcileDaily(
                new PaymentBankMutationApplicationService.ReconcileBankDailyCommand(date, "statement.csv", "bank-operasional"),
                "finance.cash"
        );

        assertThat(result.sessionId()).isEqualTo(session.getId());
        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.matchedRows()).isEqualTo(1);
        assertThat(result.exceptionRows()).isEqualTo(1);
        assertThat(exactMutation.getStatus()).isEqualTo(BankMutationStatus.MATCHED);
        assertThat(exactMutation.getMatchedPaymentId()).isEqualTo(payment.getId());
        assertThat(exceptionMutation.getStatus()).isEqualTo(BankMutationStatus.UNMATCHED);
        assertThat(exceptionMutation.getReconciliationSessionId()).isEqualTo(session.getId());
        verify(auditTrailService).record(
                "finance.cash",
                "PAYMENT",
                "RECONCILE_BANK_DAILY",
                session.getId().toString(),
                "date=2026-07-31;rows=2;matched=1;exceptions=1"
        );
    }

    @Test
    void rejectsDailyReconciliationWithoutUnmatchedRows() {
        LocalDate date = LocalDate.of(2026, 7, 31);
        when(bankMutationRepository.findByStatusAndTransactedAtGreaterThanEqualAndTransactedAtLessThanOrderByTransactedAtAsc(
                any(),
                any(),
                any()
        )).thenReturn(List.of());

        assertThatThrownBy(() -> service.reconcileDaily(
                new PaymentBankMutationApplicationService.ReconcileBankDailyCommand(date, null, "bank-operasional"),
                "finance.cash"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No unmatched bank mutations");
    }
}
