package id.pdam.sia.receivable.domain;

import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "collection_actions")
public class CollectionAction extends BaseEntity {
    @Column(nullable = false)
    private UUID customerId;

    private UUID invoiceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CollectionActionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private CollectionActionType actionType;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false)
    private Instant scheduledAt;

    private Instant completedAt;

    protected CollectionAction() {
    }

    public CollectionAction(
            UUID customerId,
            UUID invoiceId,
            CollectionActionType actionType,
            Instant scheduledAt,
            String notes
    ) {
        if (customerId == null) {
            throw new BusinessException("COLLECTION_ACTION_CUSTOMER_REQUIRED", "Collection action customer is required.");
        }
        if (actionType == null) {
            throw new BusinessException("COLLECTION_ACTION_TYPE_REQUIRED", "Collection action type is required.");
        }
        if (scheduledAt == null) {
            throw new BusinessException("COLLECTION_ACTION_SCHEDULE_REQUIRED", "Collection action schedule is required.");
        }
        this.customerId = customerId;
        this.invoiceId = invoiceId;
        this.actionType = actionType;
        this.scheduledAt = scheduledAt;
        this.notes = normalize(notes);
        this.status = CollectionActionStatus.OPEN;
    }

    public void start(Instant startedAt) {
        if (startedAt == null) {
            throw new BusinessException("COLLECTION_ACTION_STARTED_AT_REQUIRED", "Collection action start timestamp is required.");
        }
        if (status != CollectionActionStatus.OPEN) {
            throw new BusinessException("COLLECTION_ACTION_START_STATUS_INVALID", "Only open collection action can be started.");
        }
        status = CollectionActionStatus.IN_PROGRESS;
    }

    public void complete(Instant completedAt, String notes) {
        if (completedAt == null) {
            throw new BusinessException("COLLECTION_ACTION_COMPLETED_AT_REQUIRED", "Collection action completion timestamp is required.");
        }
        ensureOpenOrInProgress();
        status = CollectionActionStatus.COMPLETED;
        this.completedAt = completedAt;
        updateNotes(notes);
    }

    public void cancel(String notes) {
        ensureOpenOrInProgress();
        status = CollectionActionStatus.CANCELLED;
        completedAt = null;
        updateNotes(notes);
    }

    public void updateNotes(String notes) {
        String normalized = normalize(notes);
        if (normalized != null) {
            this.notes = normalized;
        }
    }

    private void ensureOpenOrInProgress() {
        if (status != CollectionActionStatus.OPEN && status != CollectionActionStatus.IN_PROGRESS) {
            throw new BusinessException(
                    "COLLECTION_ACTION_STATUS_INVALID",
                    "Collection action must be open or in-progress."
            );
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public CollectionActionStatus getStatus() {
        return status;
    }

    public CollectionActionType getActionType() {
        return actionType;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
