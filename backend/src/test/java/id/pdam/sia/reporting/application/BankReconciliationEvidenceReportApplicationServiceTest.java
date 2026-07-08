package id.pdam.sia.reporting.application;

import id.pdam.sia.payment.application.BankStatementRowCommand;
import id.pdam.sia.payment.application.PaymentReconciliationMatchResult;
import id.pdam.sia.payment.application.PaymentReconciliationMatchSummary;
import id.pdam.sia.payment.application.PaymentReconciliationMatchStatus;
import id.pdam.sia.payment.domain.Payment;
import id.pdam.sia.payment.domain.PaymentReconciliationItem;
import id.pdam.sia.payment.domain.PaymentReconciliationResolutionStatus;
import id.pdam.sia.payment.domain.PaymentReconciliationSession;
import id.pdam.sia.payment.domain.PaymentReconciliationSessionStatus;
import id.pdam.sia.payment.domain.PaymentStatus;
import id.pdam.sia.payment.repository.PaymentReconciliationItemRepository;
import id.pdam.sia.payment.repository.PaymentReconciliationSessionRepository;
import id.pdam.sia.shared.audit.AuditLog;
import id.pdam.sia.shared.audit.AuditLogRepository;
import id.pdam.sia.shared.audit.AuditTrailEntry;
import id.pdam.sia.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BankReconciliationEvidenceReportApplicationServiceTest {
    private static final Instant TRANSACTED_AT = Instant.parse("2026-07-31T12:00:00Z");

    private final PaymentReconciliationSessionRepository sessionRepository = mock(PaymentReconciliationSessionRepository.class);
    private final PaymentReconciliationItemRepository itemRepository = mock(PaymentReconciliationItemRepository.class);
    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final BankReconciliationEvidenceReportApplicationService service =
            new BankReconciliationEvidenceReportApplicationService(
                    sessionRepository,
                    itemRepository,
                    auditLogRepository
            );

    @Test
    void evidenceReportRequiresCompletedReconciliationSession() {
        PaymentReconciliationSession session = openSession("REC-20260731-OPEN");
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.evidenceReport(session.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only completed reconciliation sessions can be exported");
    }

    @Test
    void evidenceReportIncludesResolutionMatchedPaymentAdjustmentAndCompletionAudit() {
        PaymentReconciliationSession session = openSession("REC-20260731-EVIDENCE");
        PaymentReconciliationItem adjustedItem = PaymentReconciliationItem.from(
                session.getId(),
                PaymentReconciliationMatchResult.unmatched(1, new BankStatementRowCommand(
                        "BANK-FEE-0001",
                        new BigDecimal("2500.00"),
                        TRANSACTED_AT,
                        "bank"
                ))
        );
        adjustedItem.resolve(
                PaymentReconciliationResolutionStatus.ACCEPTED,
                "Diterima sebagai biaya admin bank.",
                "auditor.internal"
        );
        UUID adjustmentJournalId = UUID.randomUUID();
        adjustedItem.linkAdjustmentJournal(
                adjustmentJournalId,
                "Posting biaya admin bank.",
                "finance.supervisor"
        );
        Payment payment = new Payment(
                "PAY-20260731-0001",
                "idem-evidence",
                "COUNTER",
                "PAY-20260731-0001",
                new BigDecimal("100000.00"),
                TRANSACTED_AT
        );
        payment.linkSettlementJournal(UUID.randomUUID());
        PaymentReconciliationItem exactItem = PaymentReconciliationItem.from(
                session.getId(),
                PaymentReconciliationMatchResult.matched(
                        2,
                        new BankStatementRowCommand("PAY-20260731-0001", new BigDecimal("100000.00"), TRANSACTED_AT, "counter"),
                        payment,
                        PaymentReconciliationMatchStatus.EXACT_MATCH,
                        BigDecimal.ZERO,
                        "Referensi dan nominal cocok."
                )
        );
        exactItem.resolve(PaymentReconciliationResolutionStatus.ACCEPTED, "Payment cocok dengan mutasi bank.", "auditor.internal");
        session.complete("Semua item sudah ditutup untuk month-end.");
        session.signOff(
                "Evidence sudah direview dan disetujui untuk tutup bulan.",
                "finance.manager",
                "finance.supervisor"
        );
        AuditLog completionAudit = AuditLog.from(AuditTrailEntry.sensitiveAction(
                "finance.supervisor",
                "PAYMENT",
                "COMPLETE_RECONCILIATION_SESSION",
                session.getId().toString(),
                "Semua item sudah ditutup untuk month-end."
        ));

        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(itemRepository.findBySessionIdOrderByRowNumberAsc(session.getId())).thenReturn(List.of(adjustedItem, exactItem));
        when(auditLogRepository.findFirstByModuleAndActionAndRecordIdOrderByCreatedAtDesc(
                "PAYMENT",
                "COMPLETE_RECONCILIATION_SESSION",
                session.getId().toString()
        )).thenReturn(Optional.of(completionAudit));

        BankReconciliationEvidenceReport report = service.evidenceReport(session.getId());

        assertThat(report.sessionId()).isEqualTo(session.getId());
        assertThat(report.status()).isEqualTo(PaymentReconciliationSessionStatus.COMPLETED);
        assertThat(report.completedBy()).isEqualTo("finance.supervisor");
        assertThat(report.completionReason()).isEqualTo("Semua item sudah ditutup untuk month-end.");
        assertThat(report.signedOffBy()).isEqualTo("finance.manager");
        assertThat(report.signedOffAt()).isNotNull();
        assertThat(report.signOffReason()).isEqualTo("Evidence sudah direview dan disetujui untuk tutup bulan.");
        assertThat(report.summary().totalRows()).isEqualTo(2);
        assertThat(report.summary().acceptedItems()).isEqualTo(2);
        assertThat(report.summary().adjustedItems()).isEqualTo(1);
        assertThat(report.items()).hasSize(2);
        assertThat(report.items().getFirst().adjustmentJournalEntryId()).isEqualTo(adjustmentJournalId);
        assertThat(report.items().getFirst().adjustedBy()).isEqualTo("finance.supervisor");
        assertThat(report.items().getFirst().resolvedBy()).isEqualTo("auditor.internal");
        assertThat(report.items().getLast().matchedPaymentNumber()).isEqualTo("PAY-20260731-0001");
        assertThat(report.items().getLast().matchedPaymentStatus()).isEqualTo(PaymentStatus.SETTLED);
        assertThat(report.items().getLast().settlementJournalEntryId()).isNotNull();
        verify(itemRepository).findBySessionIdOrderByRowNumberAsc(session.getId());
    }

    @Test
    void evidenceCsvContainsStableMonthEndColumns() {
        PaymentReconciliationSession session = openSession("REC-20260731-CSV");
        PaymentReconciliationItem item = PaymentReconciliationItem.from(
                session.getId(),
                PaymentReconciliationMatchResult.unmatched(1, new BankStatementRowCommand(
                        "BANK-FEE-CSV",
                        new BigDecimal("2500.00"),
                        TRANSACTED_AT,
                        "bank"
                ))
        );
        item.resolve(PaymentReconciliationResolutionStatus.RESOLVED, "Tidak perlu jurnal.", "auditor.internal");
        session.complete("closed");
        session.signOff("Approved for month-end.", "finance.manager", "finance.supervisor");

        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(itemRepository.findBySessionIdOrderByRowNumberAsc(session.getId())).thenReturn(List.of(item));
        when(auditLogRepository.findFirstByModuleAndActionAndRecordIdOrderByCreatedAtDesc(
                "PAYMENT",
                "COMPLETE_RECONCILIATION_SESSION",
                session.getId().toString()
        )).thenReturn(Optional.empty());

        String csv = service.evidenceCsv(session.getId());

        assertThat(csv)
                .startsWith("session_number,row_number,statement_reference,statement_amount")
                .contains("signed_off_by,signed_off_at,sign_off_reason")
                .contains(session.getSessionNumber())
                .contains("BANK-FEE-CSV")
                .contains("RESOLVED")
                .contains("auditor.internal")
                .contains("finance.manager")
                .contains("Approved for month-end.");
    }

    private static PaymentReconciliationSession openSession(String sessionNumber) {
        return new PaymentReconciliationSession(
                sessionNumber + "-" + UUID.randomUUID().toString().substring(0, 8),
                "bank-statement.csv",
                "BCA-OPERASIONAL",
                "finance.supervisor",
                new PaymentReconciliationMatchSummary(
                        2,
                        1,
                        0,
                        0,
                        0,
                        0,
                        1,
                        BigDecimal.ZERO.setScale(2)
                )
        );
    }
}
