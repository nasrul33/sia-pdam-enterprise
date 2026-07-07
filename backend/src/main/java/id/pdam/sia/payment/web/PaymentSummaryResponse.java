package id.pdam.sia.payment.web;

import id.pdam.sia.payment.domain.Payment;
import id.pdam.sia.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentSummaryResponse(
        UUID id,
        String paymentNumber,
        String channel,
        String externalReference,
        PaymentStatus status,
        BigDecimal amount,
        Instant paidAt,
        Instant settledAt,
        Instant reversedAt,
        String reversalReason,
        UUID settlementJournalEntryId,
        UUID reversalJournalEntryId,
        Instant createdAt,
        Instant updatedAt
) {
    public static PaymentSummaryResponse from(Payment payment) {
        return new PaymentSummaryResponse(
                payment.getId(),
                payment.getPaymentNumber(),
                payment.getChannel(),
                payment.getExternalReference(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getPaidAt(),
                payment.getSettledAt(),
                payment.getReversedAt(),
                payment.getReversalReason(),
                payment.getSettlementJournalEntryId(),
                payment.getReversalJournalEntryId(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
