package id.pdam.sia.reporting.application;

import id.pdam.sia.reporting.domain.PaymentReconciliationHandoffStatus;

import java.time.LocalDate;

public record PaymentReconciliationHandoffWorkloadFilters(
        PaymentReconciliationHandoffStatus handoffStatus,
        String handoffOwner,
        LocalDate dueFrom,
        LocalDate dueTo
) {
}
