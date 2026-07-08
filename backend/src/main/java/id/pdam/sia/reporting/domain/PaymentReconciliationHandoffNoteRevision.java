package id.pdam.sia.reporting.domain;

import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "payment_reconciliation_handoff_note_revisions")
public class PaymentReconciliationHandoffNoteRevision extends BaseEntity {
    private static final int REASON_MAX_LENGTH = 500;
    private static final int ACTOR_MAX_LENGTH = 128;

    @Column(nullable = false)
    private UUID noteId;

    @Column(nullable = false)
    private int revisionNumber;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String noteText;

    @Column(length = 128)
    private String handoffOwner;

    private LocalDate handoffDueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentReconciliationHandoffStatus handoffStatus;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(nullable = false, length = 128)
    private String changedBy;

    @Column(nullable = false)
    private Instant changedAt;

    protected PaymentReconciliationHandoffNoteRevision() {
    }

    public PaymentReconciliationHandoffNoteRevision(
            PaymentReconciliationHandoffNote note,
            int revisionNumber,
            String reason,
            String actor
    ) {
        if (note == null || note.getId() == null) {
            throw new BusinessException(
                    "PAYMENT_RECONCILIATION_HANDOFF_NOTE_ID_REQUIRED",
                    "Reconciliation handoff note id is required."
            );
        }
        if (revisionNumber <= 0) {
            throw new BusinessException(
                    "PAYMENT_RECONCILIATION_HANDOFF_REVISION_INVALID",
                    "Reconciliation handoff revision number is invalid."
            );
        }
        this.noteId = note.getId();
        this.revisionNumber = revisionNumber;
        this.noteText = note.getNoteText();
        this.handoffOwner = note.getHandoffOwner();
        this.handoffDueDate = note.getHandoffDueDate();
        this.handoffStatus = note.getHandoffStatus();
        this.reason = PaymentReconciliationHandoffNote.normalizeRequired(
                reason,
                REASON_MAX_LENGTH,
                "PAYMENT_RECONCILIATION_HANDOFF_REASON_REQUIRED",
                "Reconciliation handoff revision reason is required."
        );
        this.changedBy = PaymentReconciliationHandoffNote.normalizeRequired(
                actor,
                ACTOR_MAX_LENGTH,
                "PAYMENT_RECONCILIATION_HANDOFF_ACTOR_REQUIRED",
                "Reconciliation handoff actor is required."
        );
        this.changedAt = Instant.now();
    }

    public UUID getNoteId() {
        return noteId;
    }

    public int getRevisionNumber() {
        return revisionNumber;
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

    public String getReason() {
        return reason;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
