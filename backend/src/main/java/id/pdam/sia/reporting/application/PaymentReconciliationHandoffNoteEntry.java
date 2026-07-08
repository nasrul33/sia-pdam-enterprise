package id.pdam.sia.reporting.application;

import id.pdam.sia.reporting.domain.PaymentReconciliationHandoffNote;
import id.pdam.sia.reporting.domain.PaymentReconciliationHandoffNoteRevision;
import id.pdam.sia.reporting.domain.PaymentReconciliationHandoffStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PaymentReconciliationHandoffNoteEntry(
        UUID id,
        UUID sessionId,
        String noteText,
        String handoffOwner,
        LocalDate handoffDueDate,
        PaymentReconciliationHandoffStatus handoffStatus,
        String createdBy,
        String updatedBy,
        Instant createdAt,
        Instant updatedAt,
        List<PaymentReconciliationHandoffNoteRevisionEntry> revisions
) {
    public static PaymentReconciliationHandoffNoteEntry from(
            PaymentReconciliationHandoffNote note,
            List<PaymentReconciliationHandoffNoteRevision> revisions
    ) {
        return new PaymentReconciliationHandoffNoteEntry(
                note.getId(),
                note.getSessionId(),
                note.getNoteText(),
                note.getHandoffOwner(),
                note.getHandoffDueDate(),
                note.getHandoffStatus(),
                note.getCreatedBy(),
                note.getUpdatedBy(),
                note.getCreatedAt(),
                note.getUpdatedAt(),
                revisions.stream()
                        .map(PaymentReconciliationHandoffNoteRevisionEntry::from)
                        .toList()
        );
    }
}
