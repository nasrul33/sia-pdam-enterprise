package id.pdam.sia.payment.application;

import id.pdam.sia.payment.domain.Payment;
import id.pdam.sia.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentReconciliationMatchResult(
        int rowNumber,
        String statementReference,
        BigDecimal statementAmount,
        Instant transactedAt,
        String statementChannel,
        PaymentReconciliationMatchStatus status,
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
        String message
) {
    public static PaymentReconciliationMatchResult unmatched(int rowNumber, BankStatementRowCommand row) {
        return new PaymentReconciliationMatchResult(
                rowNumber,
                row.statementReference(),
                row.amount(),
                row.transactedAt(),
                row.channel(),
                PaymentReconciliationMatchStatus.UNMATCHED,
                null,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Tidak ada payment settled/reversed yang cocok."
        );
    }

    public static PaymentReconciliationMatchResult multiple(
            int rowNumber,
            BankStatementRowCommand row,
            int candidateCount
    ) {
        return new PaymentReconciliationMatchResult(
                rowNumber,
                row.statementReference(),
                row.amount(),
                row.transactedAt(),
                row.channel(),
                PaymentReconciliationMatchStatus.MULTIPLE_CANDIDATES,
                null,
                candidateCount,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Lebih dari satu payment kandidat ditemukan; perlu review manual."
        );
    }

    public static PaymentReconciliationMatchResult matched(
            int rowNumber,
            BankStatementRowCommand row,
            Payment payment,
            PaymentReconciliationMatchStatus status,
            BigDecimal amountVariance,
            String message
    ) {
        return new PaymentReconciliationMatchResult(
                rowNumber,
                row.statementReference(),
                row.amount(),
                row.transactedAt(),
                row.channel(),
                status,
                amountVariance,
                1,
                payment.getId(),
                payment.getPaymentNumber(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getPaidAt(),
                payment.getChannel(),
                payment.getSettlementJournalEntryId(),
                payment.getReversalJournalEntryId(),
                message
        );
    }
}
