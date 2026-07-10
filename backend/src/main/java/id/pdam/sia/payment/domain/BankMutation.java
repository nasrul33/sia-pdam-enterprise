package id.pdam.sia.payment.domain;

import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bank_mutations")
public class BankMutation extends BaseEntity {
    @Column(nullable = false, unique = true, length = 128)
    private String externalReference;

    @Column(nullable = false, length = 255)
    private String sourceFilename;

    @Column(nullable = false, length = 128)
    private String bankAccountReference;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private Instant transactedAt;

    @Column(length = 64)
    private String channel;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BankMutationStatus status;

    private UUID reconciliationSessionId;

    private UUID matchedPaymentId;

    private Instant matchedAt;

    private Instant resolvedAt;

    @Column(length = 128)
    private String resolvedBy;

    @Column(columnDefinition = "TEXT")
    private String resolutionReason;

    protected BankMutation() {
    }

    public BankMutation(
            String externalReference,
            String sourceFilename,
            String bankAccountReference,
            BigDecimal amount,
            Instant transactedAt,
            String channel,
            String description
    ) {
        if (transactedAt == null) {
            throw new BusinessException("BANK_MUTATION_DATE_REQUIRED", "Bank mutation transaction timestamp is required.");
        }
        this.externalReference = require(externalReference, "BANK_MUTATION_REFERENCE_REQUIRED", "Bank mutation reference is required.");
        this.sourceFilename = require(sourceFilename, "BANK_MUTATION_SOURCE_REQUIRED", "Bank mutation source filename is required.");
        this.bankAccountReference = require(bankAccountReference, "BANK_MUTATION_ACCOUNT_REQUIRED", "Bank account reference is required.");
        this.amount = requirePositive(amount);
        this.transactedAt = transactedAt;
        this.channel = normalizeChannel(channel);
        this.description = normalize(description);
        this.status = BankMutationStatus.UNMATCHED;
    }

    public void linkSession(UUID sessionId) {
        if (sessionId == null) {
            throw new BusinessException("BANK_MUTATION_SESSION_REQUIRED", "Bank mutation reconciliation session is required.");
        }
        this.reconciliationSessionId = sessionId;
    }

    public void markMatched(UUID sessionId, UUID paymentId) {
        if (paymentId == null) {
            throw new BusinessException("BANK_MUTATION_PAYMENT_REQUIRED", "Matched payment id is required.");
        }
        linkSession(sessionId);
        this.status = BankMutationStatus.MATCHED;
        this.matchedPaymentId = paymentId;
        this.matchedAt = Instant.now();
    }

    public void resolve(String reason, String actor) {
        this.status = BankMutationStatus.RESOLVED;
        this.resolutionReason = require(reason, "BANK_MUTATION_RESOLUTION_REASON_REQUIRED", "Bank mutation resolution reason is required.");
        this.resolvedBy = require(actor, "BANK_MUTATION_RESOLVED_BY_REQUIRED", "Bank mutation resolution actor is required.");
        this.resolvedAt = Instant.now();
    }

    private static BigDecimal requirePositive(BigDecimal value) {
        BigDecimal normalized = (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
        if (normalized.signum() <= 0) {
            throw new BusinessException("BANK_MUTATION_AMOUNT_INVALID", "Bank mutation amount must be greater than zero.");
        }
        return normalized;
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

    private static String normalizeChannel(String channel) {
        String normalized = normalize(channel);
        return normalized == null ? null : normalized.toUpperCase();
    }

    public String getExternalReference() {
        return externalReference;
    }

    public String getSourceFilename() {
        return sourceFilename;
    }

    public String getBankAccountReference() {
        return bankAccountReference;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getTransactedAt() {
        return transactedAt;
    }

    public String getChannel() {
        return channel;
    }

    public String getDescription() {
        return description;
    }

    public BankMutationStatus getStatus() {
        return status;
    }

    public UUID getReconciliationSessionId() {
        return reconciliationSessionId;
    }

    public UUID getMatchedPaymentId() {
        return matchedPaymentId;
    }

    public Instant getMatchedAt() {
        return matchedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public String getResolutionReason() {
        return resolutionReason;
    }
}
