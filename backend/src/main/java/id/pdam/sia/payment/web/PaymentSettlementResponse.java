package id.pdam.sia.payment.web;

import id.pdam.sia.payment.application.PaymentSettlementResult;
import id.pdam.sia.payment.domain.PaymentAllocation;
import id.pdam.sia.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PaymentSettlementResponse(
        UUID id,
        String paymentNumber,
        String idempotencyKey,
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
        Receipt receipt,
        List<Allocation> allocations,
        Instant createdAt,
        Instant updatedAt
) {
    public static PaymentSettlementResponse from(PaymentSettlementResult result) {
        return new PaymentSettlementResponse(
                result.payment().getId(),
                result.payment().getPaymentNumber(),
                result.payment().getIdempotencyKey(),
                result.payment().getChannel(),
                result.payment().getExternalReference(),
                result.payment().getStatus(),
                result.payment().getAmount(),
                result.payment().getPaidAt(),
                result.payment().getSettledAt(),
                result.payment().getReversedAt(),
                result.payment().getReversalReason(),
                result.payment().getSettlementJournalEntryId(),
                result.payment().getReversalJournalEntryId(),
                new Receipt(
                        result.receipt().getId(),
                        result.receipt().getPaymentId(),
                        result.receipt().getReceiptNumber(),
                        result.receipt().getIssuedAt()
                ),
                result.allocations().stream().map(Allocation::from).toList(),
                result.payment().getCreatedAt(),
                result.payment().getUpdatedAt()
        );
    }

    public record Receipt(
            UUID id,
            UUID paymentId,
            String receiptNumber,
            Instant issuedAt
    ) {
    }

    public record Allocation(
            UUID id,
            UUID paymentId,
            UUID invoiceId,
            BigDecimal amount
    ) {
        private static Allocation from(PaymentAllocation allocation) {
            return new Allocation(
                    allocation.getId(),
                    allocation.getPaymentId(),
                    allocation.getInvoiceId(),
                    allocation.getAmount()
            );
        }
    }
}
