package id.pdam.sia.payment.application;

public enum PaymentReconciliationMatchStatus {
    EXACT_MATCH,
    PROBABLE_MATCH,
    AMOUNT_VARIANCE,
    REVERSED_PAYMENT,
    MULTIPLE_CANDIDATES,
    UNMATCHED
}
