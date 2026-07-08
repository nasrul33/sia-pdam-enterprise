package id.pdam.sia.reporting.application;

import id.pdam.sia.payment.application.PaymentReconciliationMatchStatus;
import id.pdam.sia.payment.domain.PaymentReconciliationItem;
import id.pdam.sia.payment.domain.PaymentReconciliationResolutionStatus;
import id.pdam.sia.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BankReconciliationEvidenceItem(
        UUID itemId,
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
        UUID adjustmentJournalEntryId,
        String adjustmentReason,
        String adjustedBy,
        Instant adjustedAt,
        PaymentReconciliationResolutionStatus resolutionStatus,
        String resolutionReason,
        String resolvedBy,
        Instant resolvedAt,
        String message
) {
    public static BankReconciliationEvidenceItem from(PaymentReconciliationItem item) {
        return new BankReconciliationEvidenceItem(
                item.getId(),
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
                item.getAdjustmentJournalEntryId(),
                item.getAdjustmentReason(),
                item.getAdjustedBy(),
                item.getAdjustedAt(),
                item.getResolutionStatus(),
                item.getResolutionReason(),
                item.getResolvedBy(),
                item.getResolvedAt(),
                item.getMessage()
        );
    }
}
