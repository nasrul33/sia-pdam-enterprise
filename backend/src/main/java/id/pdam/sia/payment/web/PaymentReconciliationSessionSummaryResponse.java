package id.pdam.sia.payment.web;

import id.pdam.sia.payment.domain.PaymentReconciliationSession;
import id.pdam.sia.payment.domain.PaymentReconciliationSessionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentReconciliationSessionSummaryResponse(
        UUID id,
        String sessionNumber,
        PaymentReconciliationSessionStatus status,
        String sourceFilename,
        String bankAccountReference,
        String createdBy,
        Instant startedAt,
        Instant completedAt,
        int totalRows,
        int exactMatches,
        int probableMatches,
        int amountVariances,
        int reversedPayments,
        int multipleCandidates,
        int unmatchedRows,
        BigDecimal totalVariance,
        Instant createdAt,
        Instant updatedAt
) {
    public static PaymentReconciliationSessionSummaryResponse from(PaymentReconciliationSession session) {
        return new PaymentReconciliationSessionSummaryResponse(
                session.getId(),
                session.getSessionNumber(),
                session.getStatus(),
                session.getSourceFilename(),
                session.getBankAccountReference(),
                session.getCreatedBy(),
                session.getStartedAt(),
                session.getCompletedAt(),
                session.getTotalRows(),
                session.getExactMatches(),
                session.getProbableMatches(),
                session.getAmountVariances(),
                session.getReversedPayments(),
                session.getMultipleCandidates(),
                session.getUnmatchedRows(),
                session.getTotalVariance(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }
}
