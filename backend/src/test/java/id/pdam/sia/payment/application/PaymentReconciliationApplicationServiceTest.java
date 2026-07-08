package id.pdam.sia.payment.application;

import id.pdam.sia.accounting.application.AccountingApplicationService;
import id.pdam.sia.accounting.application.PaymentReconciliationAdjustmentPostingCommand;
import id.pdam.sia.accounting.domain.JournalEntry;
import id.pdam.sia.payment.domain.Payment;
import id.pdam.sia.payment.domain.PaymentReconciliationItem;
import id.pdam.sia.payment.domain.PaymentReconciliationResolutionStatus;
import id.pdam.sia.payment.domain.PaymentReconciliationSession;
import id.pdam.sia.payment.domain.PaymentReconciliationSessionStatus;
import id.pdam.sia.payment.domain.PaymentStatus;
import id.pdam.sia.payment.repository.PaymentReconciliationItemRepository;
import id.pdam.sia.payment.repository.PaymentReconciliationSessionRepository;
import id.pdam.sia.payment.repository.PaymentRepository;
import id.pdam.sia.shared.audit.AuditLog;
import id.pdam.sia.shared.audit.AuditLogRepository;
import id.pdam.sia.shared.audit.AuditTrailService;
import id.pdam.sia.shared.audit.AuditTrailEntry;
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
import java.util.UUID;
import java.util.stream.StreamSupport;

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
    private final PaymentReconciliationSessionRepository paymentReconciliationSessionRepository =
            mock(PaymentReconciliationSessionRepository.class);
    private final PaymentReconciliationItemRepository paymentReconciliationItemRepository =
            mock(PaymentReconciliationItemRepository.class);
    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final AuditTrailService auditTrailService = mock(AuditTrailService.class);
    private final AccountingApplicationService accountingApplicationService = mock(AccountingApplicationService.class);
    private final PaymentReconciliationApplicationService service = new PaymentReconciliationApplicationService(
            paymentRepository,
            paymentReconciliationSessionRepository,
            paymentReconciliationItemRepository,
            auditLogRepository,
            auditTrailService,
            accountingApplicationService
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

    @Test
    void createsPersistedReconciliationSessionWithOpenItemsAndAuditTrail() {
        Payment payment = settledPayment("PAY-20260731-0010", "BANK-0010", new BigDecimal("125000.00"));
        when(paymentRepository.findByPaymentNumber("PAY-20260731-0010")).thenReturn(Optional.of(payment));
        when(paymentReconciliationSessionRepository.save(any(PaymentReconciliationSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentReconciliationItemRepository.saveAll(any()))
                .thenAnswer(invocation -> StreamSupport.stream(
                        invocation.<Iterable<PaymentReconciliationItem>>getArgument(0).spliterator(),
                        false
                ).toList());

        PaymentReconciliationSessionResult result = service.createSession(
                List.of(new BankStatementRowCommand("PAY-20260731-0010", new BigDecimal("125000.00"), PAID_AT, "counter")),
                " bank-statement.csv ",
                " bank-operasional ",
                "finance.supervisor"
        );

        assertThat(result.session().getStatus()).isEqualTo(PaymentReconciliationSessionStatus.OPEN);
        assertThat(result.session().getSourceFilename()).isEqualTo("bank-statement.csv");
        assertThat(result.session().getBankAccountReference()).isEqualTo("bank-operasional");
        assertThat(result.session().getTotalRows()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).getSessionId()).isEqualTo(result.session().getId());
        assertThat(result.items().get(0).getMatchStatus()).isEqualTo(PaymentReconciliationMatchStatus.EXACT_MATCH);
        assertThat(result.items().get(0).getResolutionStatus()).isEqualTo(PaymentReconciliationResolutionStatus.OPEN);
        verify(auditTrailService).record(
                eq("finance.supervisor"),
                eq("PAYMENT"),
                eq("CREATE_RECONCILIATION_SESSION"),
                eq(result.session().getId().toString()),
                eq("rows=1")
        );
    }

    @Test
    void resolvesSessionItemWithReasonActorAndAuditTrail() {
        PaymentReconciliationSession session = openSession("REC-20260731-RESOLVE");
        PaymentReconciliationItem item = PaymentReconciliationItem.from(
                session.getId(),
                PaymentReconciliationMatchResult.unmatched(1, new BankStatementRowCommand(
                        "BANK-UNMATCHED",
                        new BigDecimal("50000.00"),
                        PAID_AT,
                        "bank"
                ))
        );
        when(paymentReconciliationSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(paymentReconciliationItemRepository.findBySessionIdAndId(session.getId(), item.getId()))
                .thenReturn(Optional.of(item));
        when(paymentReconciliationItemRepository.save(any(PaymentReconciliationItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentReconciliationItem resolved = service.resolveItem(
                session.getId(),
                item.getId(),
                PaymentReconciliationResolutionStatus.RESOLVED,
                "Mutasi bank sudah diklasifikasi sebagai biaya administrasi.",
                "finance.supervisor"
        );

        assertThat(resolved.getResolutionStatus()).isEqualTo(PaymentReconciliationResolutionStatus.RESOLVED);
        assertThat(resolved.getResolutionReason()).isEqualTo("Mutasi bank sudah diklasifikasi sebagai biaya administrasi.");
        assertThat(resolved.getResolvedBy()).isEqualTo("finance.supervisor");
        assertThat(resolved.getResolvedAt()).isNotNull();
        verify(auditTrailService).record(
                "finance.supervisor",
                "PAYMENT",
                "RESOLVE_RECONCILIATION_ITEM",
                resolved.getId().toString(),
            "Mutasi bank sudah diklasifikasi sebagai biaya administrasi."
        );
    }

    @Test
    void createsExplicitAdjustmentJournalForAcceptedReconciliationException() {
        PaymentReconciliationSession session = openSession("REC-20260731-ADJUST");
        PaymentReconciliationItem item = PaymentReconciliationItem.from(
                session.getId(),
                PaymentReconciliationMatchResult.unmatched(7, new BankStatementRowCommand(
                        "BANK-FEE-0007",
                        new BigDecimal("2500.00"),
                        PAID_AT,
                        "bank"
                ))
        );
        item.resolve(PaymentReconciliationResolutionStatus.ACCEPTED, "Exception diterima sebagai biaya admin bank.", "auditor.internal");
        UUID debitAccountId = UUID.randomUUID();
        UUID creditAccountId = UUID.randomUUID();
        JournalEntry journal = JournalEntry.draftFromSource(
                "REC-ADJ-TEST-0007",
                UUID.randomUUID(),
                "Adjustment row 7",
                "PAYMENT_RECONCILIATION_ADJUSTMENT",
                item.getId(),
                session.getSessionNumber() + "-ROW-7"
        );

        when(paymentReconciliationSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(paymentReconciliationItemRepository.findBySessionIdAndId(session.getId(), item.getId()))
                .thenReturn(Optional.of(item));
        when(accountingApplicationService.postPaymentReconciliationAdjustment(
                any(PaymentReconciliationAdjustmentPostingCommand.class),
                eq("finance.supervisor")
        )).thenReturn(journal);
        when(paymentReconciliationItemRepository.save(any(PaymentReconciliationItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentReconciliationItemRepository.findBySessionIdOrderByRowNumberAsc(session.getId()))
                .thenReturn(List.of(item));

        PaymentReconciliationSessionResult result = service.createAdjustment(
                session.getId(),
                item.getId(),
                new PaymentReconciliationAdjustmentCommand(
                        "2026-07",
                        new BigDecimal("2500.00"),
                        debitAccountId,
                        creditAccountId,
                        "Biaya admin bank atas mutasi rekonsiliasi row 7."
                ),
                "finance.supervisor"
        );

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).getAdjustmentJournalEntryId()).isEqualTo(journal.getId());
        assertThat(result.items().get(0).getAdjustmentReason()).isEqualTo("Biaya admin bank atas mutasi rekonsiliasi row 7.");
        assertThat(result.items().get(0).getAdjustedBy()).isEqualTo("finance.supervisor");
        assertThat(result.items().get(0).getAdjustedAt()).isNotNull();
        ArgumentCaptor<PaymentReconciliationAdjustmentPostingCommand> commandCaptor =
                ArgumentCaptor.forClass(PaymentReconciliationAdjustmentPostingCommand.class);
        verify(accountingApplicationService).postPaymentReconciliationAdjustment(commandCaptor.capture(), eq("finance.supervisor"));
        assertThat(commandCaptor.getValue().reconciliationItemId()).isEqualTo(item.getId());
        assertThat(commandCaptor.getValue().sessionNumber()).isEqualTo(session.getSessionNumber());
        assertThat(commandCaptor.getValue().rowNumber()).isEqualTo(7);
        assertThat(commandCaptor.getValue().period()).isEqualTo("2026-07");
        assertThat(commandCaptor.getValue().amount()).isEqualByComparingTo("2500.00");
        assertThat(commandCaptor.getValue().debitAccountId()).isEqualTo(debitAccountId);
        assertThat(commandCaptor.getValue().creditAccountId()).isEqualTo(creditAccountId);
        verify(auditTrailService).record(
                "finance.supervisor",
                "PAYMENT",
                "CREATE_RECONCILIATION_ADJUSTMENT",
                item.getId().toString(),
                "Biaya admin bank atas mutasi rekonsiliasi row 7."
        );
    }

    @Test
    void rejectsAdjustmentJournalForOpenReconciliationItem() {
        PaymentReconciliationSession session = openSession("REC-20260731-OPEN-ADJ");
        PaymentReconciliationItem item = PaymentReconciliationItem.from(
                session.getId(),
                PaymentReconciliationMatchResult.unmatched(1, new BankStatementRowCommand(
                        "BANK-OPEN",
                        new BigDecimal("50000.00"),
                        PAID_AT,
                        "bank"
                ))
        );

        when(paymentReconciliationSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(paymentReconciliationItemRepository.findBySessionIdAndId(session.getId(), item.getId()))
                .thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.createAdjustment(
                session.getId(),
                item.getId(),
                new PaymentReconciliationAdjustmentCommand(
                        "2026-07",
                        new BigDecimal("50000.00"),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "open item adjustment"
                ),
                "finance.supervisor"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must be accepted before adjustment");

        verify(accountingApplicationService, never()).postPaymentReconciliationAdjustment(any(), any());
        verify(paymentReconciliationItemRepository, never()).save(any(PaymentReconciliationItem.class));
    }

    @Test
    void rejectsCompletingSessionWhenOpenItemsRemain() {
        PaymentReconciliationSession session = openSession("REC-20260731-OPEN");
        when(paymentReconciliationSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(paymentReconciliationItemRepository.countBySessionIdAndResolutionStatus(
                session.getId(),
                PaymentReconciliationResolutionStatus.OPEN
        )).thenReturn(1L);

        assertThatThrownBy(() -> service.completeSession(session.getId(), "closing", "finance.supervisor"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("All reconciliation items must be resolved");

        verify(paymentReconciliationSessionRepository, never()).save(any(PaymentReconciliationSession.class));
    }

    @Test
    void completesSessionAfterAllItemsAreResolved() {
        PaymentReconciliationSession session = openSession("REC-20260731-DONE");
        PaymentReconciliationItem item = PaymentReconciliationItem.from(
                session.getId(),
                PaymentReconciliationMatchResult.unmatched(1, new BankStatementRowCommand(
                        "BANK-RESOLVED",
                        new BigDecimal("75000.00"),
                        PAID_AT,
                        "bank"
                ))
        );
        item.resolve(PaymentReconciliationResolutionStatus.IGNORED, "Duplikasi mutasi bank sudah dikonfirmasi.", "auditor.internal");
        when(paymentReconciliationSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(paymentReconciliationItemRepository.countBySessionIdAndResolutionStatus(
                session.getId(),
                PaymentReconciliationResolutionStatus.OPEN
        )).thenReturn(0L);
        when(paymentReconciliationSessionRepository.save(any(PaymentReconciliationSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentReconciliationItemRepository.findBySessionIdOrderByRowNumberAsc(session.getId()))
                .thenReturn(List.of(item));

        PaymentReconciliationSessionResult result = service.completeSession(
                session.getId(),
                "Semua exception sudah ditindaklanjuti.",
                "finance.supervisor"
        );

        assertThat(result.session().getStatus()).isEqualTo(PaymentReconciliationSessionStatus.COMPLETED);
        assertThat(result.session().getCompletedAt()).isNotNull();
        assertThat(result.items()).hasSize(1);
        verify(auditTrailService).record(
                "finance.supervisor",
                "PAYMENT",
                "COMPLETE_RECONCILIATION_SESSION",
                session.getId().toString(),
                "Semua exception sudah ditindaklanjuti."
        );
    }

    @Test
    void signsOffCompletedSessionWithSodAwareAuditTrail() {
        PaymentReconciliationSession session = openSession("REC-20260731-SIGNOFF");
        session.complete("Semua item sudah ditutup.");
        PaymentReconciliationItem item = PaymentReconciliationItem.from(
                session.getId(),
                PaymentReconciliationMatchResult.unmatched(1, new BankStatementRowCommand(
                        "BANK-SIGNOFF",
                        new BigDecimal("75000.00"),
                        PAID_AT,
                        "bank"
                ))
        );
        AuditLog completionAudit = AuditLog.from(AuditTrailEntry.sensitiveAction(
                "finance.supervisor",
                "PAYMENT",
                "COMPLETE_RECONCILIATION_SESSION",
                session.getId().toString(),
                "Semua item sudah ditutup."
        ));
        when(paymentReconciliationSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(auditLogRepository.findFirstByModuleAndActionAndRecordIdOrderByCreatedAtDesc(
                "PAYMENT",
                "COMPLETE_RECONCILIATION_SESSION",
                session.getId().toString()
        )).thenReturn(Optional.of(completionAudit));
        when(paymentReconciliationSessionRepository.save(any(PaymentReconciliationSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentReconciliationItemRepository.findBySessionIdOrderByRowNumberAsc(session.getId()))
                .thenReturn(List.of(item));

        PaymentReconciliationSessionResult result = service.signOffSession(
                session.getId(),
                "Evidence sudah direview dan saldo kas-bank disetujui.",
                "finance.manager"
        );

        assertThat(result.session().getSignedOffBy()).isEqualTo("finance.manager");
        assertThat(result.session().getSignedOffAt()).isNotNull();
        assertThat(result.session().getSignOffReason()).isEqualTo("Evidence sudah direview dan saldo kas-bank disetujui.");
        assertThat(result.items()).hasSize(1);
        verify(auditTrailService).record(
                "finance.manager",
                "PAYMENT",
                "SIGN_OFF_RECONCILIATION_SESSION",
                session.getId().toString(),
                "Evidence sudah direview dan saldo kas-bank disetujui."
        );
    }

    @Test
    void rejectsSignOffBeforeSessionCompletion() {
        PaymentReconciliationSession session = openSession("REC-20260731-SIGNOFF-OPEN");
        when(paymentReconciliationSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(auditLogRepository.findFirstByModuleAndActionAndRecordIdOrderByCreatedAtDesc(
                "PAYMENT",
                "COMPLETE_RECONCILIATION_SESSION",
                session.getId().toString()
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.signOffSession(session.getId(), "approve", "finance.manager"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only completed reconciliation sessions can be signed off");

        verify(paymentReconciliationSessionRepository, never()).save(any(PaymentReconciliationSession.class));
    }

    @Test
    void rejectsSignOffByCreatorOrCompletionActor() {
        PaymentReconciliationSession session = openSession("REC-20260731-SIGNOFF-SOD");
        session.complete("Selesai.");
        AuditLog completionAudit = AuditLog.from(AuditTrailEntry.sensitiveAction(
                "finance.completer",
                "PAYMENT",
                "COMPLETE_RECONCILIATION_SESSION",
                session.getId().toString(),
                "Selesai."
        ));
        when(paymentReconciliationSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(auditLogRepository.findFirstByModuleAndActionAndRecordIdOrderByCreatedAtDesc(
                "PAYMENT",
                "COMPLETE_RECONCILIATION_SESSION",
                session.getId().toString()
        )).thenReturn(Optional.of(completionAudit));

        assertThatThrownBy(() -> service.signOffSession(session.getId(), "approve", "finance.supervisor"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must be different from creator and completer");
        assertThatThrownBy(() -> service.signOffSession(session.getId(), "approve", "finance.completer"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must be different from creator and completer");

        verify(paymentReconciliationSessionRepository, never()).save(any(PaymentReconciliationSession.class));
    }

    @Test
    void rejectsDuplicateSignOff() {
        PaymentReconciliationSession session = openSession("REC-20260731-SIGNOFF-DUP");
        session.complete("Selesai.");
        session.signOff("Approved pertama.", "finance.manager", "finance.completer");
        when(paymentReconciliationSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(auditLogRepository.findFirstByModuleAndActionAndRecordIdOrderByCreatedAtDesc(
                "PAYMENT",
                "COMPLETE_RECONCILIATION_SESSION",
                session.getId().toString()
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.signOffSession(session.getId(), "Approved ulang.", "finance.director"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already been signed off");

        verify(paymentReconciliationSessionRepository, never()).save(any(PaymentReconciliationSession.class));
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

    private static PaymentReconciliationSession openSession(String sessionNumber) {
        PaymentReconciliationMatchSummary summary = new PaymentReconciliationMatchSummary(
                1,
                0,
                0,
                0,
                0,
                0,
                1,
                BigDecimal.ZERO.setScale(2)
        );
        return new PaymentReconciliationSession(
                sessionNumber + "-" + UUID.randomUUID().toString().substring(0, 8),
                null,
                null,
                "finance.supervisor",
                summary
        );
    }
}
