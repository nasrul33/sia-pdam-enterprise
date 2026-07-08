package id.pdam.sia.reporting.web;

import id.pdam.sia.reporting.application.PaymentReconciliationHandoffNoteRevisionEntry;
import id.pdam.sia.reporting.domain.PaymentReconciliationHandoffStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentReconciliationHandoffNoteRevisionResponse(
        UUID id,
        UUID noteId,
        int revisionNumber,
        String noteText,
        String handoffOwner,
        LocalDate handoffDueDate,
        PaymentReconciliationHandoffStatus handoffStatus,
        String reason,
        String changedBy,
        Instant changedAt
) {
    static PaymentReconciliationHandoffNoteRevisionResponse from(PaymentReconciliationHandoffNoteRevisionEntry entry) {
        return new PaymentReconciliationHandoffNoteRevisionResponse(
                entry.id(),
                entry.noteId(),
                entry.revisionNumber(),
                entry.noteText(),
                entry.handoffOwner(),
                entry.handoffDueDate(),
                entry.handoffStatus(),
                entry.reason(),
                entry.changedBy(),
                entry.changedAt()
        );
    }
}
