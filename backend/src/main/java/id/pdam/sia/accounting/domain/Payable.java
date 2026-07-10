package id.pdam.sia.accounting.domain;

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
@Table(name = "payables")
public class Payable extends BaseEntity {
    @Column(nullable = false)
    private UUID supplierId;

    @Column(nullable = false, unique = true, length = 64)
    private String payableNumber;

    @Column(length = 128)
    private String supplierReference;

    @Column(nullable = false, length = 7)
    private String period;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PayableStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(nullable = false)
    private Instant recordedAt;

    @Column(nullable = false, length = 128)
    private String recordedBy;

    private Instant paidAt;

    @Column(length = 128)
    private String paidBy;

    @Column(nullable = false)
    private UUID recordedJournalEntryId;

    private UUID settlementJournalEntryId;

    protected Payable() {
    }

    public Payable(
            UUID supplierId,
            String payableNumber,
            String supplierReference,
            String period,
            BigDecimal amount,
            String description,
            UUID recordedJournalEntryId,
            String actor
    ) {
        if (supplierId == null) {
            throw new BusinessException("PAYABLE_SUPPLIER_REQUIRED", "Payable supplier is required.");
        }
        if (recordedJournalEntryId == null) {
            throw new BusinessException("PAYABLE_RECORDED_JOURNAL_REQUIRED", "Payable recorded journal is required.");
        }
        this.supplierId = supplierId;
        this.payableNumber = require(payableNumber, "PAYABLE_NUMBER_REQUIRED", "Payable number is required.");
        this.supplierReference = normalize(supplierReference);
        this.period = require(period, "PAYABLE_PERIOD_REQUIRED", "Payable period is required.");
        this.amount = requirePositive(amount, "PAYABLE_AMOUNT_REQUIRED", "Payable amount is required.");
        this.description = require(description, "PAYABLE_DESCRIPTION_REQUIRED", "Payable description is required.");
        this.recordedJournalEntryId = recordedJournalEntryId;
        this.recordedAt = Instant.now();
        this.recordedBy = require(actor, "PAYABLE_RECORDED_BY_REQUIRED", "Payable recorded actor is required.");
        this.status = PayableStatus.OPEN;
    }

    public void settle(UUID settlementJournalEntryId, String actor) {
        if (status != PayableStatus.OPEN) {
            throw new BusinessException("PAYABLE_SETTLE_STATUS_INVALID", "Only open payable can be settled.");
        }
        if (settlementJournalEntryId == null) {
            throw new BusinessException("PAYABLE_SETTLEMENT_JOURNAL_REQUIRED", "Payable settlement journal is required.");
        }
        this.status = PayableStatus.PAID;
        this.settlementJournalEntryId = settlementJournalEntryId;
        this.paidAt = Instant.now();
        this.paidBy = require(actor, "PAYABLE_PAID_BY_REQUIRED", "Payable paid actor is required.");
    }

    private static BigDecimal requirePositive(BigDecimal value, String code, String message) {
        if (value == null) {
            throw new BusinessException(code, message);
        }
        BigDecimal normalized = value.setScale(2, RoundingMode.HALF_UP);
        if (normalized.signum() <= 0) {
            throw new BusinessException("PAYABLE_AMOUNT_INVALID", "Payable amount must be greater than zero.");
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

    public UUID getSupplierId() {
        return supplierId;
    }

    public String getPayableNumber() {
        return payableNumber;
    }

    public String getSupplierReference() {
        return supplierReference;
    }

    public String getPeriod() {
        return period;
    }

    public PayableStatus getStatus() {
        return status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public String getRecordedBy() {
        return recordedBy;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public String getPaidBy() {
        return paidBy;
    }

    public UUID getRecordedJournalEntryId() {
        return recordedJournalEntryId;
    }

    public UUID getSettlementJournalEntryId() {
        return settlementJournalEntryId;
    }
}
