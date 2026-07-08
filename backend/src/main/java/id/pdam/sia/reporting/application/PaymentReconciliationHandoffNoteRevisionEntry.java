package id.pdam.sia.reporting.application;

import id.pdam.sia.reporting.domain.PaymentReconciliationHandoffNoteRevision;
import id.pdam.sia.reporting.domain.PaymentReconciliationHandoffStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentReconciliationHandoffNoteRevisionEntry(
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
    public static PaymentReconciliationHandoffNoteRevisionEntry from(PaymentReconciliationHandoffNoteRevision revision) {
        return new PaymentReconciliationHandoffNoteRevisionEntry(
                revision.getId(),
                revision.getNoteId(),
                revision.getRevisionNumber(),
                revision.getNoteText(),
                revision.getHandoffOwner(),
                revision.getHandoffDueDate(),
                revision.getHandoffStatus(),
                revision.getReason(),
                revision.getChangedBy(),
                revision.getChangedAt()
        );
    }
}
