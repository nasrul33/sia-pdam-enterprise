package id.pdam.sia.reporting.application;

import id.pdam.sia.payment.domain.PaymentReconciliationItem;
import id.pdam.sia.payment.domain.PaymentReconciliationSession;
import id.pdam.sia.payment.domain.PaymentReconciliationSessionStatus;
import id.pdam.sia.payment.repository.PaymentReconciliationItemRepository;
import id.pdam.sia.payment.repository.PaymentReconciliationSessionRepository;
import id.pdam.sia.shared.audit.AuditLog;
import id.pdam.sia.shared.audit.AuditLogRepository;
import id.pdam.sia.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class BankReconciliationEvidenceReportApplicationService {
    private static final String PAYMENT_MODULE = "PAYMENT";
    private static final String COMPLETE_ACTION = "COMPLETE_RECONCILIATION_SESSION";

    private final PaymentReconciliationSessionRepository sessionRepository;
    private final PaymentReconciliationItemRepository itemRepository;
    private final AuditLogRepository auditLogRepository;

    public BankReconciliationEvidenceReportApplicationService(
            PaymentReconciliationSessionRepository sessionRepository,
            PaymentReconciliationItemRepository itemRepository,
            AuditLogRepository auditLogRepository
    ) {
        this.sessionRepository = sessionRepository;
        this.itemRepository = itemRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public BankReconciliationEvidenceReport evidenceReport(UUID sessionId) {
        if (sessionId == null) {
            throw new BusinessException(
                    "PAYMENT_RECONCILIATION_EVIDENCE_SESSION_REQUIRED",
                    "Reconciliation session id is required."
            );
        }
        PaymentReconciliationSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(
                        "PAYMENT_RECONCILIATION_SESSION_NOT_FOUND",
                        "Reconciliation session was not found."
                ));
        if (session.getStatus() != PaymentReconciliationSessionStatus.COMPLETED) {
            throw new BusinessException(
                    "PAYMENT_RECONCILIATION_EVIDENCE_SESSION_NOT_COMPLETED",
                    "Only completed reconciliation sessions can be exported as evidence."
            );
        }

        List<PaymentReconciliationItem> items = itemRepository.findBySessionIdOrderByRowNumberAsc(session.getId());
        Optional<AuditLog> completionAudit = auditLogRepository.findFirstByModuleAndActionAndRecordIdOrderByCreatedAtDesc(
                PAYMENT_MODULE,
                COMPLETE_ACTION,
                session.getId().toString()
        );

        return new BankReconciliationEvidenceReport(
                session.getId(),
                session.getSessionNumber(),
                session.getStatus(),
                session.getSourceFilename(),
                session.getBankAccountReference(),
                session.getCreatedBy(),
                session.getStartedAt(),
                completionAudit.map(AuditLog::getCreatedAt).orElse(session.getCompletedAt()),
                completionAudit.map(AuditLog::getActor).orElse(null),
                completionAudit.map(AuditLog::getReason).orElse(null),
                session.getSignedOffAt(),
                session.getSignedOffBy(),
                session.getSignOffReason(),
                BankReconciliationEvidenceSummary.from(items),
                items.stream().map(BankReconciliationEvidenceItem::from).toList(),
                Instant.now()
        );
    }

    @Transactional(readOnly = true)
    public String evidenceCsv(UUID sessionId) {
        return evidenceCsv(evidenceReport(sessionId));
    }

    public String evidenceCsv(BankReconciliationEvidenceReport report) {
        if (report == null) {
            throw new BusinessException(
                    "PAYMENT_RECONCILIATION_EVIDENCE_REPORT_REQUIRED",
                    "Reconciliation evidence report is required."
            );
        }
        StringBuilder builder = new StringBuilder();
        appendRow(
                builder,
                "session_number",
                "row_number",
                "statement_reference",
                "statement_amount",
                "transacted_at",
                "statement_channel",
                "match_status",
                "amount_variance",
                "candidate_count",
                "matched_payment_number",
                "matched_payment_status",
                "matched_payment_amount",
                "settlement_journal_entry_id",
                "reversal_journal_entry_id",
                "adjustment_journal_entry_id",
                "resolution_status",
                "resolution_reason",
                "resolved_by",
                "resolved_at",
                "adjustment_reason",
                "adjusted_by",
                "adjusted_at",
                "completed_by",
                "completed_at",
                "completion_reason",
                "signed_off_by",
                "signed_off_at",
                "sign_off_reason"
        );

        report.items().forEach(item -> appendRow(
                builder,
                report.sessionNumber(),
                item.rowNumber(),
                item.statementReference(),
                item.statementAmount(),
                item.transactedAt(),
                item.statementChannel(),
                item.matchStatus(),
                item.amountVariance(),
                item.candidateCount(),
                item.matchedPaymentNumber(),
                item.matchedPaymentStatus(),
                item.matchedPaymentAmount(),
                item.settlementJournalEntryId(),
                item.reversalJournalEntryId(),
                item.adjustmentJournalEntryId(),
                item.resolutionStatus(),
                item.resolutionReason(),
                item.resolvedBy(),
                item.resolvedAt(),
                item.adjustmentReason(),
                item.adjustedBy(),
                item.adjustedAt(),
                report.completedBy(),
                report.completedAt(),
                report.completionReason(),
                report.signedOffBy(),
                report.signedOffAt(),
                report.signOffReason()
        ));
        return builder.toString();
    }

    private static void appendRow(StringBuilder builder, Object... values) {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            appendValue(builder, values[index]);
        }
        builder.append('\n');
    }

    private static void appendValue(StringBuilder builder, Object value) {
        if (value == null) {
            return;
        }
        String text = value.toString();
        boolean quoted = text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r");
        if (!quoted) {
            builder.append(text);
            return;
        }

        builder.append('"');
        builder.append(text.replace("\"", "\"\""));
        builder.append('"');
    }
}
