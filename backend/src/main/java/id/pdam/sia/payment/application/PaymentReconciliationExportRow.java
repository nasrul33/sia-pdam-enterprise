package id.pdam.sia.payment.application;

import id.pdam.sia.payment.domain.Payment;
import id.pdam.sia.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentReconciliationExportRow(
        UUID paymentId,
        String paymentNumber,
        String channel,
        String externalReference,
        PaymentStatus status,
        BigDecimal amount,
        Instant paidAt,
        Instant settledAt,
        Instant reversedAt,
        UUID settlementJournalEntryId,
        UUID reversalJournalEntryId
) {
    public static PaymentReconciliationExportRow from(Payment payment) {
        return new PaymentReconciliationExportRow(
                payment.getId(),
                payment.getPaymentNumber(),
                payment.getChannel(),
                payment.getExternalReference(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getPaidAt(),
                payment.getSettledAt(),
                payment.getReversedAt(),
                payment.getSettlementJournalEntryId(),
                payment.getReversalJournalEntryId()
        );
    }
}
