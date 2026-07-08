package id.pdam.sia.reporting.application;

import id.pdam.sia.reporting.domain.PaymentReconciliationHandoffStatus;

import java.time.LocalDate;

public record PaymentReconciliationHandoffNoteCommand(
        String noteText,
        String handoffOwner,
        LocalDate handoffDueDate,
        PaymentReconciliationHandoffStatus handoffStatus,
        String reason
) {
}
