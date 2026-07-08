package id.pdam.sia.reporting.application;

import id.pdam.sia.payment.domain.PaymentReconciliationSessionStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BankReconciliationEvidenceReport(
        UUID sessionId,
        String sessionNumber,
        PaymentReconciliationSessionStatus status,
        String sourceFilename,
        String bankAccountReference,
        String createdBy,
        Instant startedAt,
        Instant completedAt,
        String completedBy,
        String completionReason,
        Instant signedOffAt,
        String signedOffBy,
        String signOffReason,
        BankReconciliationEvidenceSummary summary,
        List<BankReconciliationEvidenceItem> items,
        Instant generatedAt
) {
    public BankReconciliationEvidenceReport {
        items = List.copyOf(items);
    }
}
