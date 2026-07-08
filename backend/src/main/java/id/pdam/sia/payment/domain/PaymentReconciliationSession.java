package id.pdam.sia.payment.domain;

import id.pdam.sia.payment.application.PaymentReconciliationMatchSummary;
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

@Entity
@Table(name = "payment_reconciliation_sessions")
public class PaymentReconciliationSession extends BaseEntity {
    @Column(nullable = false, unique = true, length = 64)
    private String sessionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentReconciliationSessionStatus status;

    @Column(length = 255)
    private String sourceFilename;

    @Column(length = 128)
    private String bankAccountReference;

    @Column(nullable = false, length = 128)
    private String createdBy;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant completedAt;

    @Column(length = 128)
    private String signedOffBy;

    private Instant signedOffAt;

    @Column(columnDefinition = "TEXT")
    private String signOffReason;

    @Column(nullable = false)
    private int totalRows;

    @Column(nullable = false)
    private int exactMatches;

    @Column(nullable = false)
    private int probableMatches;

    @Column(nullable = false)
    private int amountVariances;

    @Column(nullable = false)
    private int reversedPayments;

    @Column(nullable = false)
    private int multipleCandidates;

    @Column(nullable = false)
    private int unmatchedRows;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalVariance;

    protected PaymentReconciliationSession() {
    }

    public PaymentReconciliationSession(
            String sessionNumber,
            String sourceFilename,
            String bankAccountReference,
            String createdBy,
            PaymentReconciliationMatchSummary summary
    ) {
        this.sessionNumber = require(sessionNumber, "PAYMENT_RECONCILIATION_SESSION_NUMBER_REQUIRED", "Reconciliation session number is required.");
        this.sourceFilename = normalizeOptional(sourceFilename, 255);
        this.bankAccountReference = normalizeOptional(bankAccountReference, 128);
        this.createdBy = require(createdBy, "PAYMENT_RECONCILIATION_CREATED_BY_REQUIRED", "Reconciliation creator is required.");
        this.startedAt = Instant.now();
        this.status = PaymentReconciliationSessionStatus.OPEN;
        applySummary(summary);
    }

    public void complete(String reason) {
        require(reason, "PAYMENT_RECONCILIATION_COMPLETE_REASON_REQUIRED", "Reconciliation completion reason is required.");
        ensureOpen();
        this.status = PaymentReconciliationSessionStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void signOff(String reason, String actor, String completionActor) {
        String normalizedReason = require(reason, "PAYMENT_RECONCILIATION_SIGN_OFF_REASON_REQUIRED", "Reconciliation sign-off reason is required.");
        String normalizedActor = require(actor, "PAYMENT_RECONCILIATION_SIGN_OFF_ACTOR_REQUIRED", "Reconciliation sign-off actor is required.");
        if (status != PaymentReconciliationSessionStatus.COMPLETED) {
            throw new BusinessException(
                    "PAYMENT_RECONCILIATION_SESSION_NOT_COMPLETED",
                    "Only completed reconciliation sessions can be signed off."
            );
        }
        if (signedOffAt != null) {
            throw new BusinessException(
                    "PAYMENT_RECONCILIATION_SIGN_OFF_ALREADY_DONE",
                    "Reconciliation evidence has already been signed off."
            );
        }
        if (sameActor(normalizedActor, createdBy) || sameActor(normalizedActor, completionActor)) {
            throw new BusinessException(
                    "PAYMENT_RECONCILIATION_SIGN_OFF_SOD_VIOLATION",
                    "Reconciliation sign-off actor must be different from creator and completer."
            );
        }

        this.signedOffBy = normalizedActor;
        this.signedOffAt = Instant.now();
        this.signOffReason = normalizedReason;
    }

    public void ensureOpen() {
        if (status != PaymentReconciliationSessionStatus.OPEN) {
            throw new BusinessException("PAYMENT_RECONCILIATION_SESSION_NOT_OPEN", "Reconciliation session is not open.");
        }
    }

    private void applySummary(PaymentReconciliationMatchSummary summary) {
        if (summary == null) {
            throw new BusinessException("PAYMENT_RECONCILIATION_SUMMARY_REQUIRED", "Reconciliation summary is required.");
        }
        this.totalRows = summary.totalRows();
        this.exactMatches = summary.exactMatches();
        this.probableMatches = summary.probableMatches();
        this.amountVariances = summary.amountVariances();
        this.reversedPayments = summary.reversedPayments();
        this.multipleCandidates = summary.multipleCandidates();
        this.unmatchedRows = summary.unmatchedRows();
        this.totalVariance = summary.totalVariance().setScale(2, RoundingMode.HALF_UP);
    }

    private static String require(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    private static String normalizeOptional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new BusinessException("PAYMENT_RECONCILIATION_FIELD_TOO_LONG", "Reconciliation field exceeds max length.");
        }
        return normalized;
    }

    private static boolean sameActor(String actor, String otherActor) {
        return otherActor != null && actor.equalsIgnoreCase(otherActor.trim());
    }

    public String getSessionNumber() {
        return sessionNumber;
    }

    public PaymentReconciliationSessionStatus getStatus() {
        return status;
    }

    public String getSourceFilename() {
        return sourceFilename;
    }

    public String getBankAccountReference() {
        return bankAccountReference;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getSignedOffBy() {
        return signedOffBy;
    }

    public Instant getSignedOffAt() {
        return signedOffAt;
    }

    public String getSignOffReason() {
        return signOffReason;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public int getExactMatches() {
        return exactMatches;
    }

    public int getProbableMatches() {
        return probableMatches;
    }

    public int getAmountVariances() {
        return amountVariances;
    }

    public int getReversedPayments() {
        return reversedPayments;
    }

    public int getMultipleCandidates() {
        return multipleCandidates;
    }

    public int getUnmatchedRows() {
        return unmatchedRows;
    }

    public BigDecimal getTotalVariance() {
        return totalVariance;
    }
}
