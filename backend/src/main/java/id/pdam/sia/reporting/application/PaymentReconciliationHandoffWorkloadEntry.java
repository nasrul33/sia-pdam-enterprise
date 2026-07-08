package id.pdam.sia.reporting.application;

import id.pdam.sia.payment.domain.PaymentReconciliationSession;
import id.pdam.sia.reporting.domain.PaymentReconciliationHandoffNote;
import id.pdam.sia.reporting.domain.PaymentReconciliationHandoffStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentReconciliationHandoffWorkloadEntry(
        UUID noteId,
        UUID sessionId,
        String sessionNumber,
        String bankAccountReference,
        Instant completedAt,
        PaymentReconciliationReviewStatus reviewStatus,
        String signedOffBy,
        Instant signedOffAt,
        String noteText,
        String handoffOwner,
        LocalDate handoffDueDate,
        PaymentReconciliationHandoffStatus handoffStatus,
        long revisionCount,
        long overdueDays,
        String createdBy,
        String updatedBy,
        Instant createdAt,
        Instant updatedAt,
        Instant generatedAt
) {
    public static PaymentReconciliationHandoffWorkloadEntry from(
            PaymentReconciliationHandoffNote note,
            PaymentReconciliationSession session,
            long revisionCount,
            LocalDate generatedDate,
            Instant generatedAt
    ) {
        PaymentReconciliationReviewStatus reviewStatus = session.getSignedOffAt() == null
                ? PaymentReconciliationReviewStatus.PENDING_SIGN_OFF
                : PaymentReconciliationReviewStatus.SIGNED_OFF;
        return new PaymentReconciliationHandoffWorkloadEntry(
                note.getId(),
                note.getSessionId(),
                session.getSessionNumber(),
                session.getBankAccountReference(),
                session.getCompletedAt(),
                reviewStatus,
                session.getSignedOffBy(),
                session.getSignedOffAt(),
                note.getNoteText(),
                note.getHandoffOwner(),
                note.getHandoffDueDate(),
                note.getHandoffStatus(),
                revisionCount,
                BankReconciliationHandoffWorkloadApplicationService.overdueDays(note, generatedDate),
                note.getCreatedBy(),
                note.getUpdatedBy(),
                note.getCreatedAt(),
                note.getUpdatedAt(),
                generatedAt
        );
    }
}
