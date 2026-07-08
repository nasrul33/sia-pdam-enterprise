package id.pdam.sia.reporting.application;

import id.pdam.sia.payment.domain.PaymentReconciliationItem;
import id.pdam.sia.payment.domain.PaymentReconciliationSession;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

public record BankReconciliationReviewRegisterEntry(
        UUID sessionId,
        String sessionNumber,
        PaymentReconciliationReviewStatus reviewStatus,
        String sourceFilename,
        String bankAccountReference,
        String createdBy,
        Instant startedAt,
        Instant completedAt,
        String signedOffBy,
        Instant signedOffAt,
        String signOffReason,
        int totalRows,
        int exactMatches,
        int probableMatches,
        int exceptionItems,
        int amountVariances,
        int reversedPayments,
        int multipleCandidates,
        int unmatchedRows,
        int acceptedItems,
        int resolvedItems,
        int ignoredItems,
        int adjustedItems,
        BigDecimal totalVariance,
        long pendingSignOffAgeDays,
        Instant generatedAt
) {
    public static BankReconciliationReviewRegisterEntry from(
            PaymentReconciliationSession session,
            List<PaymentReconciliationItem> items,
            Instant generatedAt
    ) {
        BankReconciliationEvidenceSummary summary = BankReconciliationEvidenceSummary.from(items);
        int exceptionItems = summary.amountVariances()
                + summary.reversedPayments()
                + summary.multipleCandidates()
                + summary.unmatchedRows();
        PaymentReconciliationReviewStatus reviewStatus = session.getSignedOffAt() == null
                ? PaymentReconciliationReviewStatus.PENDING_SIGN_OFF
                : PaymentReconciliationReviewStatus.SIGNED_OFF;

        return new BankReconciliationReviewRegisterEntry(
                session.getId(),
                session.getSessionNumber(),
                reviewStatus,
                session.getSourceFilename(),
                session.getBankAccountReference(),
                session.getCreatedBy(),
                session.getStartedAt(),
                session.getCompletedAt(),
                session.getSignedOffBy(),
                session.getSignedOffAt(),
                session.getSignOffReason(),
                summary.totalRows(),
                summary.exactMatches(),
                summary.probableMatches(),
                exceptionItems,
                summary.amountVariances(),
                summary.reversedPayments(),
                summary.multipleCandidates(),
                summary.unmatchedRows(),
                summary.acceptedItems(),
                summary.resolvedItems(),
                summary.ignoredItems(),
                summary.adjustedItems(),
                summary.totalVariance(),
                pendingSignOffAgeDays(session, generatedAt),
                generatedAt
        );
    }

    private static long pendingSignOffAgeDays(PaymentReconciliationSession session, Instant generatedAt) {
        if (session.getSignedOffAt() != null || session.getCompletedAt() == null) {
            return 0;
        }
        return Math.max(0, ChronoUnit.DAYS.between(session.getCompletedAt(), generatedAt));
    }
}
