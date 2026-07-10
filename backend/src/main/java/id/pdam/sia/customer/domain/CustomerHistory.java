package id.pdam.sia.customer.domain;

import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_histories")
public class CustomerHistory extends BaseEntity {
    @Column(nullable = false)
    private UUID customerId;

    @Column(nullable = false, length = 64)
    private String changeType;

    @Column(columnDefinition = "TEXT")
    private String beforeValue;

    @Column(columnDefinition = "TEXT")
    private String afterValue;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(nullable = false, length = 128)
    private String changedBy;

    @Column(nullable = false)
    private Instant changedAt;

    protected CustomerHistory() {
    }

    public CustomerHistory(UUID customerId, String changeType, String beforeValue, String afterValue, String reason, String actor) {
        if (customerId == null) {
            throw new BusinessException("CUSTOMER_HISTORY_CUSTOMER_REQUIRED", "Customer history customer is required.");
        }
        this.customerId = customerId;
        this.changeType = require(changeType, "CUSTOMER_HISTORY_TYPE_REQUIRED", "Customer history change type is required.");
        this.beforeValue = normalize(beforeValue);
        this.afterValue = normalize(afterValue);
        this.reason = require(reason, "CUSTOMER_HISTORY_REASON_REQUIRED", "Customer history reason is required.");
        this.changedBy = require(actor, "CUSTOMER_HISTORY_ACTOR_REQUIRED", "Customer history actor is required.");
        this.changedAt = Instant.now();
    }

    private static String require(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
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

    public String getChangeType() {
        return changeType;
    }

    public String getBeforeValue() {
        return beforeValue;
    }

    public String getAfterValue() {
        return afterValue;
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
