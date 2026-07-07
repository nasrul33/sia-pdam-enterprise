package id.pdam.sia.payment.domain;

import id.pdam.sia.payment.application.PaymentReconciliationMatchResult;
import id.pdam.sia.payment.application.PaymentReconciliationMatchStatus;
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
@Table(name = "payment_reconciliation_items")
public class PaymentReconciliationItem extends BaseEntity {
    @Column(nullable = false)
    private UUID sessionId;

    @Column(nullable = false)
    private int rowNumber;

    @Column(nullable = false, length = 128)
    private String statementReference;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal statementAmount;

    @Column(nullable = false)
    private Instant transactedAt;

    @Column(length = 64)
    private String statementChannel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentReconciliationMatchStatus matchStatus;

    @Column(precision = 19, scale = 2)
    private BigDecimal amountVariance;

    @Column(nullable = false)
    private int candidateCount;

    private UUID matchedPaymentId;

    @Column(length = 64)
    private String matchedPaymentNumber;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private PaymentStatus matchedPaymentStatus;

    @Column(precision = 19, scale = 2)
    private BigDecimal matchedPaymentAmount;

    private Instant matchedPaymentPaidAt;

    @Column(length = 64)
    private String matchedPaymentChannel;

    private UUID settlementJournalEntryId;

    private UUID reversalJournalEntryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentReconciliationResolutionStatus resolutionStatus;

    @Column(columnDefinition = "TEXT")
    private String resolutionReason;

    @Column(length = 128)
    private String resolvedBy;

    private Instant resolvedAt;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    protected PaymentReconciliationItem() {
    }

    private PaymentReconciliationItem(UUID sessionId, PaymentReconciliationMatchResult result) {
        if (sessionId == null) {
            throw new BusinessException("PAYMENT_RECONCILIATION_SESSION_ID_REQUIRED", "Reconciliation session id is required.");
        }
        this.sessionId = sessionId;
        this.rowNumber = result.rowNumber();
        this.statementReference = result.statementReference();
        this.statementAmount = money(result.statementAmount());
        this.transactedAt = result.transactedAt();
        this.statementChannel = result.statementChannel();
        this.matchStatus = result.status();
        this.amountVariance = result.amountVariance() == null ? null : result.amountVariance().setScale(2, RoundingMode.HALF_UP);
        this.candidateCount = result.candidateCount();
        this.matchedPaymentId = result.matchedPaymentId();
        this.matchedPaymentNumber = result.matchedPaymentNumber();
        this.matchedPaymentStatus = result.matchedPaymentStatus();
        this.matchedPaymentAmount = result.matchedPaymentAmount() == null ? null : result.matchedPaymentAmount().setScale(2, RoundingMode.HALF_UP);
        this.matchedPaymentPaidAt = result.matchedPaymentPaidAt();
        this.matchedPaymentChannel = result.matchedPaymentChannel();
        this.settlementJournalEntryId = result.settlementJournalEntryId();
        this.reversalJournalEntryId = result.reversalJournalEntryId();
        this.resolutionStatus = PaymentReconciliationResolutionStatus.OPEN;
        this.message = require(result.message(), "PAYMENT_RECONCILIATION_MESSAGE_REQUIRED", "Reconciliation item message is required.");
    }

    public static PaymentReconciliationItem from(UUID sessionId, PaymentReconciliationMatchResult result) {
        return new PaymentReconciliationItem(sessionId, result);
    }

    public void resolve(PaymentReconciliationResolutionStatus resolutionStatus, String reason, String actor) {
        if (resolutionStatus == null || resolutionStatus == PaymentReconciliationResolutionStatus.OPEN) {
            throw new BusinessException("PAYMENT_RECONCILIATION_RESOLUTION_STATUS_INVALID", "Resolution status must close the item.");
        }
        this.resolutionReason = require(reason, "PAYMENT_RECONCILIATION_RESOLUTION_REASON_REQUIRED", "Resolution reason is required.");
        this.resolvedBy = require(actor, "PAYMENT_RECONCILIATION_RESOLVED_BY_REQUIRED", "Resolution actor is required.");
        this.resolutionStatus = resolutionStatus;
        this.resolvedAt = Instant.now();
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null) {
            throw new BusinessException("PAYMENT_RECONCILIATION_AMOUNT_REQUIRED", "Reconciliation item amount is required.");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String require(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public String getStatementReference() {
        return statementReference;
    }

    public BigDecimal getStatementAmount() {
        return statementAmount;
    }

    public Instant getTransactedAt() {
        return transactedAt;
    }

    public String getStatementChannel() {
        return statementChannel;
    }

    public PaymentReconciliationMatchStatus getMatchStatus() {
        return matchStatus;
    }

    public BigDecimal getAmountVariance() {
        return amountVariance;
    }

    public int getCandidateCount() {
        return candidateCount;
    }

    public UUID getMatchedPaymentId() {
        return matchedPaymentId;
    }

    public String getMatchedPaymentNumber() {
        return matchedPaymentNumber;
    }

    public PaymentStatus getMatchedPaymentStatus() {
        return matchedPaymentStatus;
    }

    public BigDecimal getMatchedPaymentAmount() {
        return matchedPaymentAmount;
    }

    public Instant getMatchedPaymentPaidAt() {
        return matchedPaymentPaidAt;
    }

    public String getMatchedPaymentChannel() {
        return matchedPaymentChannel;
    }

    public UUID getSettlementJournalEntryId() {
        return settlementJournalEntryId;
    }

    public UUID getReversalJournalEntryId() {
        return reversalJournalEntryId;
    }

    public PaymentReconciliationResolutionStatus getResolutionStatus() {
        return resolutionStatus;
    }

    public String getResolutionReason() {
        return resolutionReason;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public String getMessage() {
        return message;
    }
}
