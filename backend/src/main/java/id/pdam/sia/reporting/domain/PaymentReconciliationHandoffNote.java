package id.pdam.sia.reporting.domain;

import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "payment_reconciliation_handoff_notes")
public class PaymentReconciliationHandoffNote extends BaseEntity {
    private static final int NOTE_TEXT_MAX_LENGTH = 2000;
    private static final int ACTOR_MAX_LENGTH = 128;
    private static final int OWNER_MAX_LENGTH = 128;

    @Column(nullable = false)
    private UUID sessionId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String noteText;

    @Column(length = 128)
    private String handoffOwner;

    private LocalDate handoffDueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentReconciliationHandoffStatus handoffStatus;

    @Column(nullable = false, length = 128)
    private String createdBy;

    @Column(nullable = false, length = 128)
    private String updatedBy;

    protected PaymentReconciliationHandoffNote() {
    }

    public PaymentReconciliationHandoffNote(
            UUID sessionId,
            String noteText,
            String handoffOwner,
            LocalDate handoffDueDate,
            PaymentReconciliationHandoffStatus handoffStatus,
            String actor
    ) {
        if (sessionId == null) {
            throw new BusinessException(
                    "PAYMENT_RECONCILIATION_HANDOFF_SESSION_ID_REQUIRED",
                    "Reconciliation handoff session id is required."
            );
        }
        String normalizedActor = normalizeRequired(
                actor,
                ACTOR_MAX_LENGTH,
                "PAYMENT_RECONCILIATION_HANDOFF_ACTOR_REQUIRED",
                "Reconciliation handoff actor is required."
        );
        this.sessionId = sessionId;
        this.noteText = normalizeRequired(
                noteText,
                NOTE_TEXT_MAX_LENGTH,
                "PAYMENT_RECONCILIATION_HANDOFF_NOTE_REQUIRED",
                "Reconciliation handoff note is required."
        );
        this.handoffOwner = normalizeOptional(handoffOwner, OWNER_MAX_LENGTH);
        this.handoffDueDate = handoffDueDate;
        this.handoffStatus = requireStatus(handoffStatus);
        this.createdBy = normalizedActor;
        this.updatedBy = normalizedActor;
    }

    public void revise(
            String noteText,
            String handoffOwner,
            LocalDate handoffDueDate,
            PaymentReconciliationHandoffStatus handoffStatus,
            String actor
    ) {
        this.noteText = normalizeRequired(
                noteText,
                NOTE_TEXT_MAX_LENGTH,
                "PAYMENT_RECONCILIATION_HANDOFF_NOTE_REQUIRED",
                "Reconciliation handoff note is required."
        );
        this.handoffOwner = normalizeOptional(handoffOwner, OWNER_MAX_LENGTH);
        this.handoffDueDate = handoffDueDate;
        this.handoffStatus = requireStatus(handoffStatus);
        this.updatedBy = normalizeRequired(
                actor,
                ACTOR_MAX_LENGTH,
                "PAYMENT_RECONCILIATION_HANDOFF_ACTOR_REQUIRED",
                "Reconciliation handoff actor is required."
        );
    }

    private static PaymentReconciliationHandoffStatus requireStatus(PaymentReconciliationHandoffStatus status) {
        if (status == null) {
            throw new BusinessException(
                    "PAYMENT_RECONCILIATION_HANDOFF_STATUS_REQUIRED",
                    "Reconciliation handoff status is required."
            );
        }
        return status;
    }

    static String normalizeRequired(String value, int maxLength, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new BusinessException(
                    "PAYMENT_RECONCILIATION_HANDOFF_FIELD_TOO_LONG",
                    "Reconciliation handoff field exceeds max length."
            );
        }
        return normalized;
    }

    static String normalizeOptional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new BusinessException(
                    "PAYMENT_RECONCILIATION_HANDOFF_FIELD_TOO_LONG",
                    "Reconciliation handoff field exceeds max length."
            );
        }
        return normalized;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public String getNoteText() {
        return noteText;
    }

    public String getHandoffOwner() {
        return handoffOwner;
    }

    public LocalDate getHandoffDueDate() {
        return handoffDueDate;
    }

    public PaymentReconciliationHandoffStatus getHandoffStatus() {
        return handoffStatus;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }
}
