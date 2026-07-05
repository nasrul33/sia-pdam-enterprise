package id.pdam.sia.payment.application;

import id.pdam.sia.payment.domain.Payment;
import id.pdam.sia.payment.domain.PaymentAllocation;
import id.pdam.sia.payment.domain.PaymentReceipt;

import java.util.List;

public record PaymentSettlementResult(
        Payment payment,
        PaymentReceipt receipt,
        List<PaymentAllocation> allocations
) {
    public PaymentSettlementResult {
        allocations = List.copyOf(allocations);
    }
}
