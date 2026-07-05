package id.pdam.sia.shared.idempotency;

import id.pdam.sia.shared.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyRecord {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(nullable = false, length = 64)
    private String module;

    @Column(nullable = false, length = 128)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IdempotencyStatus status;

    @Column(length = 128)
    private String responseReference;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant completedAt;

    @Version
    private long version;

    protected IdempotencyRecord() {
    }

    private IdempotencyRecord(String idempotencyKey, String module, String requestHash, Instant expiresAt) {
        this.idempotencyKey = require(idempotencyKey, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency key is required.");
        this.module = require(module, "IDEMPOTENCY_MODULE_REQUIRED", "Idempotency module is required.");
        this.requestHash = require(requestHash, "IDEMPOTENCY_HASH_REQUIRED", "Request hash is required.");
        if (expiresAt == null || !expiresAt.isAfter(Instant.now())) {
            throw new BusinessException("IDEMPOTENCY_EXPIRY_INVALID", "Idempotency expiry must be in the future.");
        }
        this.expiresAt = expiresAt;
        this.status = IdempotencyStatus.PENDING;
    }

    public static IdempotencyRecord reserve(String idempotencyKey, String module, String requestHash, Instant expiresAt) {
        return new IdempotencyRecord(idempotencyKey, module, requestHash, expiresAt);
    }

    public void ensureSameRequest(String requestHash) {
        if (!Objects.equals(this.requestHash, requestHash)) {
            throw new BusinessException(
                    "IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD",
                    "Idempotency key was already used for a different payload."
            );
        }
    }

    public void markCompleted(String responseReference) {
        if (status == IdempotencyStatus.COMPLETED) {
            return;
        }
        this.responseReference = require(
                responseReference,
                "IDEMPOTENCY_RESPONSE_REQUIRED",
                "Idempotency response reference is required."
        );
        this.status = IdempotencyStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public boolean isCompleted() {
        return status == IdempotencyStatus.COMPLETED;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    private static String require(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public IdempotencyStatus getStatus() {
        return status;
    }
}
