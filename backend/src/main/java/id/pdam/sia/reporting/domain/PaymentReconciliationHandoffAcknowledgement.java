package id.pdam.sia.reporting.domain;

import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "payment_reconciliation_handoff_acknowledgements")
public class PaymentReconciliationHandoffAcknowledgement extends BaseEntity {
    private static final int HASH_MAX_LENGTH = 128;
    private static final int ACTOR_MAX_LENGTH = 128;
    private static final int REASON_MAX_LENGTH = 500;

    @Column(nullable = false, unique = true, length = 128)
    private String packetScopeHash;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String filterSnapshot;

    @Column(nullable = false)
    private long staleNoteCount;

    @Column(nullable = false)
    private long ownerCount;

    @Column(nullable = false)
    private long maxOverdueDays;

    @Column(nullable = false, length = 128)
    private String acknowledgedBy;

    @Column(nullable = false)
    private Instant acknowledgedAt;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    protected PaymentReconciliationHandoffAcknowledgement() {
    }

    public PaymentReconciliationHandoffAcknowledgement(
            String packetScopeHash,
            String filterSnapshot,
            long staleNoteCount,
            long ownerCount,
            long maxOverdueDays,
            String acknowledgedBy,
            Instant acknowledgedAt,
            String reason
    ) {
        if (staleNoteCount <= 0) {
            throw new BusinessException(
                    "PAYMENT_RECONCILIATION_HANDOFF_ACK_EMPTY_PACKET",
                    "Stale handoff acknowledgement requires at least one stale note."
            );
        }
        if (ownerCount <= 0) {
            throw new BusinessException(
                    "PAYMENT_RECONCILIATION_HANDOFF_ACK_OWNER_REQUIRED",
                    "Stale handoff acknowledgement owner count is required."
            );
        }
        if (maxOverdueDays <= 0) {
            throw new BusinessException(
                    "PAYMENT_RECONCILIATION_HANDOFF_ACK_OVERDUE_REQUIRED",
                    "Stale handoff acknowledgement requires overdue workload."
            );
        }
        if (acknowledgedAt == null) {
            throw new BusinessException(
                    "PAYMENT_RECONCILIATION_HANDOFF_ACK_TIME_REQUIRED",
                    "Stale handoff acknowledgement timestamp is required."
            );
        }
        this.packetScopeHash = normalizeRequired(
                packetScopeHash,
                HASH_MAX_LENGTH,
                "PAYMENT_RECONCILIATION_HANDOFF_ACK_HASH_REQUIRED",
                "Stale handoff packet scope hash is required."
        );
        this.filterSnapshot = normalizeRequired(
                filterSnapshot,
                2_000,
                "PAYMENT_RECONCILIATION_HANDOFF_ACK_FILTER_REQUIRED",
                "Stale handoff acknowledgement filter snapshot is required."
        );
        this.staleNoteCount = staleNoteCount;
        this.ownerCount = ownerCount;
        this.maxOverdueDays = maxOverdueDays;
        this.acknowledgedBy = normalizeRequired(
                acknowledgedBy,
                ACTOR_MAX_LENGTH,
                "PAYMENT_RECONCILIATION_HANDOFF_ACK_ACTOR_REQUIRED",
                "Stale handoff acknowledgement actor is required."
        );
        this.acknowledgedAt = acknowledgedAt;
        this.reason = normalizeRequired(
                reason,
                REASON_MAX_LENGTH,
                "PAYMENT_RECONCILIATION_HANDOFF_ACK_REASON_REQUIRED",
                "Stale handoff acknowledgement reason is required."
        );
    }

    private static String normalizeRequired(String value, int maxLength, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new BusinessException(
                    "PAYMENT_RECONCILIATION_HANDOFF_ACK_FIELD_TOO_LONG",
                    "Stale handoff acknowledgement field exceeds max length."
            );
        }
        return normalized;
    }

    public String getPacketScopeHash() {
        return packetScopeHash;
    }

    public String getFilterSnapshot() {
        return filterSnapshot;
    }

    public long getStaleNoteCount() {
        return staleNoteCount;
    }

    public long getOwnerCount() {
        return ownerCount;
    }

    public long getMaxOverdueDays() {
        return maxOverdueDays;
    }

    public String getAcknowledgedBy() {
        return acknowledgedBy;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public String getReason() {
        return reason;
    }
}
