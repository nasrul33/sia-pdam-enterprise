package id.pdam.sia.payment.application;

import id.pdam.sia.payment.domain.PaymentStatus;

import java.time.Instant;

public record PaymentReconciliationFilters(
        PaymentStatus status,
        String channel,
        Instant paidAtFrom,
        Instant paidAtTo
) {
}
