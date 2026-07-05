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
@Table(name = "payments")
public class Payment extends BaseEntity {
    @Column(nullable = false, unique = true, length = 64)
    private String paymentNumber;

    @Column(nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(nullable = false, length = 64)
    private String channel;

    @Column(length = 128)
    private String externalReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private Instant paidAt;

    private Instant settledAt;

    private Instant reversedAt;

    private UUID settlementJournalEntryId;

    private UUID reversalJournalEntryId;

    @Column(columnDefinition = "TEXT")
    private String reversalReason;

    protected Payment() {
    }

    public Payment(
            String paymentNumber,
            String idempotencyKey,
            String channel,
            String externalReference,
            BigDecimal amount,
            Instant paidAt
    ) {
        if (paidAt == null) {
            throw new BusinessException("PAYMENT_PAID_AT_REQUIRED", "Payment paid timestamp is required.");
        }
        this.paymentNumber = require(paymentNumber, "PAYMENT_NUMBER_REQUIRED", "Payment number is required.");
        this.idempotencyKey = require(idempotencyKey, "PAYMENT_IDEMPOTENCY_KEY_REQUIRED", "Payment idempotency key is required.");
        this.channel = require(channel, "PAYMENT_CHANNEL_REQUIRED", "Payment channel is required.");
        this.externalReference = normalizeOptional(externalReference);
        this.amount = requirePositive(amount, "PAYMENT_AMOUNT_REQUIRED", "Payment amount is required.");
        this.paidAt = paidAt;
        this.settledAt = paidAt;
        this.status = PaymentStatus.SETTLED;
    }

    public void linkSettlementJournal(UUID settlementJournalEntryId) {
        if (settlementJournalEntryId == null) {
            throw new BusinessException("PAYMENT_SETTLEMENT_JOURNAL_REQUIRED", "Payment settlement journal is required.");
        }
        if (this.settlementJournalEntryId != null) {
            throw new BusinessException("PAYMENT_SETTLEMENT_JOURNAL_ALREADY_LINKED", "Payment already has settlement journal.");
        }
        this.settlementJournalEntryId = settlementJournalEntryId;
    }

    public void ensureCanReverse() {
        if (status != PaymentStatus.SETTLED) {
            throw new BusinessException("PAYMENT_REVERSAL_STATUS_INVALID", "Only settled payment can be reversed.");
        }
        if (settlementJournalEntryId == null) {
            throw new BusinessException("PAYMENT_SETTLEMENT_JOURNAL_MISSING", "Payment settlement journal is required before reversal.");
        }
    }

    public void reverse(Instant reversedAt, String reversalReason, UUID reversalJournalEntryId) {
        if (reversedAt == null) {
            throw new BusinessException("PAYMENT_REVERSED_AT_REQUIRED", "Payment reversed timestamp is required.");
        }
        if (reversalReason == null || reversalReason.isBlank()) {
            throw new BusinessException("PAYMENT_REVERSAL_REASON_REQUIRED", "Payment reversal reason is required.");
        }
        if (reversalJournalEntryId == null) {
            throw new BusinessException("PAYMENT_REVERSAL_JOURNAL_REQUIRED", "Payment reversal journal is required.");
        }
        ensureCanReverse();
        this.status = PaymentStatus.REVERSED;
        this.reversedAt = reversedAt;
        this.reversalReason = reversalReason.trim();
        this.reversalJournalEntryId = reversalJournalEntryId;
    }

    private static BigDecimal requirePositive(BigDecimal value, String code, String message) {
        if (value == null) {
            throw new BusinessException(code, message);
        }
        BigDecimal normalized = value.setScale(2, RoundingMode.HALF_UP);
        if (normalized.signum() <= 0) {
            throw new BusinessException("PAYMENT_AMOUNT_INVALID", "Payment amount must be greater than zero.");
        }
        return normalized;
    }

    private static String require(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public String getPaymentNumber() {
        return paymentNumber;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getChannel() {
        return channel;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public Instant getReversedAt() {
        return reversedAt;
    }

    public UUID getSettlementJournalEntryId() {
        return settlementJournalEntryId;
    }

    public UUID getReversalJournalEntryId() {
        return reversalJournalEntryId;
    }

    public String getReversalReason() {
        return reversalReason;
    }
}
