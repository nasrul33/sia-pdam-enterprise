package id.pdam.sia.reporting.application;

import java.time.Instant;

public record BankReconciliationReviewRegisterFilters(
        PaymentReconciliationReviewStatus signOffStatus,
        Instant completedFrom,
        Instant completedTo
) {
}
