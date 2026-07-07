package id.pdam.sia.payment.web;

import id.pdam.sia.payment.application.PaymentReconciliationMatchStatus;
import id.pdam.sia.payment.application.PaymentReconciliationSessionResult;
import id.pdam.sia.payment.domain.PaymentReconciliationItem;
import id.pdam.sia.payment.domain.PaymentReconciliationResolutionStatus;
import id.pdam.sia.payment.domain.PaymentReconciliationSession;
import id.pdam.sia.payment.domain.PaymentReconciliationSessionStatus;
import id.pdam.sia.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PaymentReconciliationSessionResponse(
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
        List<Item> items,
        Instant createdAt,
        Instant updatedAt
) {
    public static PaymentReconciliationSessionResponse from(PaymentReconciliationSessionResult result) {
        return from(result.session(), result.items());
    }

    public static PaymentReconciliationSessionResponse from(PaymentReconciliationSession session, List<PaymentReconciliationItem> items) {
        return new PaymentReconciliationSessionResponse(
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
                items.stream().map(Item::from).toList(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }

    public record Item(
            UUID id,
            UUID sessionId,
            int rowNumber,
            String statementReference,
            BigDecimal statementAmount,
            Instant transactedAt,
            String statementChannel,
            PaymentReconciliationMatchStatus matchStatus,
            BigDecimal amountVariance,
            int candidateCount,
            UUID matchedPaymentId,
            String matchedPaymentNumber,
            PaymentStatus matchedPaymentStatus,
            BigDecimal matchedPaymentAmount,
            Instant matchedPaymentPaidAt,
            String matchedPaymentChannel,
            UUID settlementJournalEntryId,
            UUID reversalJournalEntryId,
            PaymentReconciliationResolutionStatus resolutionStatus,
            String resolutionReason,
            String resolvedBy,
            Instant resolvedAt,
            String message,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static Item from(PaymentReconciliationItem item) {
            return new Item(
                    item.getId(),
                    item.getSessionId(),
                    item.getRowNumber(),
                    item.getStatementReference(),
                    item.getStatementAmount(),
                    item.getTransactedAt(),
                    item.getStatementChannel(),
                    item.getMatchStatus(),
                    item.getAmountVariance(),
                    item.getCandidateCount(),
                    item.getMatchedPaymentId(),
                    item.getMatchedPaymentNumber(),
                    item.getMatchedPaymentStatus(),
                    item.getMatchedPaymentAmount(),
                    item.getMatchedPaymentPaidAt(),
                    item.getMatchedPaymentChannel(),
                    item.getSettlementJournalEntryId(),
                    item.getReversalJournalEntryId(),
                    item.getResolutionStatus(),
                    item.getResolutionReason(),
                    item.getResolvedBy(),
                    item.getResolvedAt(),
                    item.getMessage(),
                    item.getCreatedAt(),
                    item.getUpdatedAt()
            );
        }
    }
}
