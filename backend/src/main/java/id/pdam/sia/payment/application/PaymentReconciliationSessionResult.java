package id.pdam.sia.payment.application;

import id.pdam.sia.payment.domain.PaymentReconciliationItem;
import id.pdam.sia.payment.domain.PaymentReconciliationSession;

import java.util.List;

public record PaymentReconciliationSessionResult(
        PaymentReconciliationSession session,
        List<PaymentReconciliationItem> items
) {
    public PaymentReconciliationSessionResult {
        items = List.copyOf(items);
    }
}
