package id.pdam.sia.shared.audit;

import id.pdam.sia.shared.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, length = 128)
    private String actor;

    @Column(nullable = false, length = 64)
    private String module;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(length = 128)
    private String recordId;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String beforeValue;

    @Column(columnDefinition = "TEXT")
    private String afterValue;

    @Column(length = 128)
    private String correlationId;

    @Column(length = 64)
    private String ipAddress;

    @Column(length = 255)
    private String userAgent;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {
    }

    private AuditLog(AuditTrailEntry entry) {
        this.actor = require(entry.actor(), "AUDIT_ACTOR_REQUIRED", "Audit actor is required.");
        this.module = require(entry.module(), "AUDIT_MODULE_REQUIRED", "Audit module is required.");
        this.action = require(entry.action(), "AUDIT_ACTION_REQUIRED", "Audit action is required.");
        this.recordId = normalize(entry.recordId());
        this.reason = normalize(entry.reason());
        this.beforeValue = normalize(entry.beforeValue());
        this.afterValue = normalize(entry.afterValue());
        this.correlationId = normalize(entry.correlationId());
        this.ipAddress = normalize(entry.ipAddress());
        this.userAgent = normalize(entry.userAgent());
    }

    public static AuditLog from(AuditTrailEntry entry) {
        return new AuditLog(entry);
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
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

    public UUID getId() {
        return id;
    }

    public String getActor() {
        return actor;
    }

    public String getModule() {
        return module;
    }

    public String getAction() {
        return action;
    }
}
