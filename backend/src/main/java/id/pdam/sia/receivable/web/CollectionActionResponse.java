package id.pdam.sia.receivable.web;

import id.pdam.sia.receivable.domain.CollectionAction;
import id.pdam.sia.receivable.domain.CollectionActionStatus;
import id.pdam.sia.receivable.domain.CollectionActionType;

import java.time.Instant;
import java.util.UUID;

public record CollectionActionResponse(
        UUID id,
        UUID customerId,
        UUID invoiceId,
        CollectionActionStatus status,
        CollectionActionType actionType,
        String notes,
        Instant scheduledAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static CollectionActionResponse from(CollectionAction action) {
        return new CollectionActionResponse(
                action.getId(),
                action.getCustomerId(),
                action.getInvoiceId(),
                action.getStatus(),
                action.getActionType(),
                action.getNotes(),
                action.getScheduledAt(),
                action.getCompletedAt(),
                action.getCreatedAt(),
                action.getUpdatedAt()
        );
    }
}
