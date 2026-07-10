package id.pdam.sia.shared.audit;

import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "audit_chain_entries")
public class AuditChainEntry extends BaseEntity {
    @Column(insertable = false, updatable = false)
    private Long sequenceNo;

    @Column(nullable = false, unique = true)
    private UUID auditLogId;

    @Column(length = 128)
    private String previousHash;

    @Column(nullable = false, length = 128)
    private String entryHash;

    protected AuditChainEntry() {
    }

    public AuditChainEntry(UUID auditLogId, String previousHash, String entryHash) {
        if (auditLogId == null) {
            throw new BusinessException("AUDIT_CHAIN_LOG_REQUIRED", "Audit chain log id is required.");
        }
        this.auditLogId = auditLogId;
        this.previousHash = previousHash;
        this.entryHash = require(entryHash, "AUDIT_CHAIN_HASH_REQUIRED", "Audit chain hash is required.");
    }

    private static String require(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    public Long getSequenceNo() {
        return sequenceNo;
    }

    public UUID getAuditLogId() {
        return auditLogId;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public String getEntryHash() {
        return entryHash;
    }
}
