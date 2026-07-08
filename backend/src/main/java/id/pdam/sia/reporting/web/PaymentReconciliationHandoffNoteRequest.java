package id.pdam.sia.reporting.web;

import id.pdam.sia.reporting.application.PaymentReconciliationHandoffNoteCommand;
import id.pdam.sia.reporting.domain.PaymentReconciliationHandoffStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record PaymentReconciliationHandoffNoteRequest(
        @NotBlank @Size(max = 2000) String noteText,
        @Size(max = 128) String handoffOwner,
        LocalDate handoffDueDate,
        @NotNull PaymentReconciliationHandoffStatus handoffStatus,
        @NotBlank @Size(max = 500) String reason
) {
    PaymentReconciliationHandoffNoteCommand toCommand() {
        return new PaymentReconciliationHandoffNoteCommand(
                noteText,
                handoffOwner,
                handoffDueDate,
                handoffStatus,
                reason
        );
    }
}
