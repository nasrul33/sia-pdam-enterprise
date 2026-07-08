package id.pdam.sia.reporting.web;

import id.pdam.sia.reporting.application.PaymentReconciliationHandoffNoteEntry;
import id.pdam.sia.reporting.domain.PaymentReconciliationHandoffStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PaymentReconciliationHandoffNoteResponse(
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
        List<PaymentReconciliationHandoffNoteRevisionResponse> revisions
) {
    static PaymentReconciliationHandoffNoteResponse from(PaymentReconciliationHandoffNoteEntry entry) {
        return new PaymentReconciliationHandoffNoteResponse(
                entry.id(),
                entry.sessionId(),
                entry.noteText(),
                entry.handoffOwner(),
                entry.handoffDueDate(),
                entry.handoffStatus(),
                entry.createdBy(),
                entry.updatedBy(),
                entry.createdAt(),
                entry.updatedAt(),
                entry.revisions().stream()
                        .map(PaymentReconciliationHandoffNoteRevisionResponse::from)
                        .toList()
        );
    }
}
