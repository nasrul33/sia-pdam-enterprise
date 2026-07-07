package id.pdam.sia.payment.application;

import java.util.List;

public record PaymentReconciliationMatchReport(
        List<PaymentReconciliationMatchResult> matches,
        PaymentReconciliationMatchSummary summary
) {
    public PaymentReconciliationMatchReport {
        matches = List.copyOf(matches);
    }

    public static PaymentReconciliationMatchReport from(List<PaymentReconciliationMatchResult> matches) {
        return new PaymentReconciliationMatchReport(matches, PaymentReconciliationMatchSummary.from(matches));
    }
}
