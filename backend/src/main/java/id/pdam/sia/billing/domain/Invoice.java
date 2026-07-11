package id.pdam.sia.billing.domain;

import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "invoices")
public class Invoice extends BaseEntity {
    private UUID billingBatchId;

    @Column(nullable = false)
    private UUID connectionId;

    @Column(nullable = false, unique = true, length = 64)
    private String invoiceNumber;

    @Column(nullable = false, length = 7)
    private String period;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InvoiceStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal usageCharge;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal fixedCharge;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal levyCharge;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal adminCharge;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal wasteCharge;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal penaltyAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal paidAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal outstandingAmount;

    private Instant issuedAt;

    private UUID issueJournalEntryId;

    private Instant voidedAt;

    private UUID voidJournalEntryId;

    @Column(nullable = false)
    private LocalDate dueDate;

    protected Invoice() {
    }

    public Invoice(
            UUID billingBatchId,
            UUID connectionId,
            String invoiceNumber,
            String period,
            BigDecimal subtotal,
            LocalDate dueDate
    ) {
        this(billingBatchId, connectionId, invoiceNumber, period, subtotal, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, dueDate);
    }

    public Invoice(
            UUID billingBatchId,
            UUID connectionId,
            String invoiceNumber,
            String period,
            BigDecimal usageCharge,
            BigDecimal fixedCharge,
            BigDecimal levyCharge,
            BigDecimal adminCharge,
            BigDecimal wasteCharge,
            BigDecimal penaltyAmount,
            LocalDate dueDate
    ) {
        if (billingBatchId == null) {
            throw new BusinessException("INVOICE_BATCH_REQUIRED", "Invoice billing batch is required.");
        }
        if (connectionId == null) {
            throw new BusinessException("INVOICE_CONNECTION_REQUIRED", "Invoice connection is required.");
        }
        if (dueDate == null) {
            throw new BusinessException("INVOICE_DUE_DATE_REQUIRED", "Invoice due date is required.");
        }
        this.billingBatchId = billingBatchId;
        this.connectionId = connectionId;
        this.invoiceNumber = require(invoiceNumber, "INVOICE_NUMBER_REQUIRED", "Invoice number is required.");
        this.period = require(period, "INVOICE_PERIOD_REQUIRED", "Invoice period is required.");
        this.usageCharge = requireNonNegative(usageCharge, "INVOICE_USAGE_CHARGE_REQUIRED", "Invoice usage charge is required.");
        this.fixedCharge = requireNonNegative(fixedCharge, "INVOICE_FIXED_CHARGE_REQUIRED", "Invoice fixed charge is required.");
        this.levyCharge = requireNonNegative(levyCharge, "INVOICE_LEVY_CHARGE_REQUIRED", "Invoice levy charge is required.");
        this.adminCharge = requireNonNegative(adminCharge, "INVOICE_ADMIN_CHARGE_REQUIRED", "Invoice admin charge is required.");
        this.wasteCharge = requireNonNegative(wasteCharge, "INVOICE_WASTE_CHARGE_REQUIRED", "Invoice waste charge is required.");
        this.subtotal = this.usageCharge.add(this.fixedCharge).add(this.levyCharge).add(this.adminCharge).add(this.wasteCharge).setScale(2);
        this.penaltyAmount = requireNonNegative(penaltyAmount, "INVOICE_PENALTY_REQUIRED", "Invoice penalty is required.");
        this.paidAmount = BigDecimal.ZERO.setScale(2);
        this.outstandingAmount = this.subtotal.add(this.penaltyAmount).setScale(2);
        this.dueDate = dueDate;
        this.status = InvoiceStatus.DRAFT;
    }

    public void markIssued(Instant issuedAt) {
        if (issuedAt == null) {
            throw new BusinessException("INVOICE_ISSUED_AT_REQUIRED", "Invoice issued timestamp is required.");
        }
        if (status != InvoiceStatus.DRAFT) {
            throw new BusinessException("INVOICE_ISSUE_STATUS_INVALID", "Only draft invoice can be issued.");
        }
        this.status = InvoiceStatus.ISSUED;
        this.issuedAt = issuedAt;
    }

    public void markIssued(Instant issuedAt, UUID issueJournalEntryId) {
        if (issueJournalEntryId == null) {
            throw new BusinessException("INVOICE_ISSUE_JOURNAL_REQUIRED", "Invoice issue journal is required.");
        }
        markIssued(issuedAt);
        this.issueJournalEntryId = issueJournalEntryId;
    }

    public void applyPayment(BigDecimal amount) {
        BigDecimal normalizedAmount = requirePositive(amount, "INVOICE_PAYMENT_AMOUNT_REQUIRED", "Invoice payment amount is required.");
        if (status != InvoiceStatus.ISSUED && status != InvoiceStatus.PARTIAL_PAID) {
            throw new BusinessException("INVOICE_PAYMENT_STATUS_INVALID", "Payment can only be applied to issued or partial paid invoice.");
        }
        if (normalizedAmount.compareTo(outstandingAmount) > 0) {
            throw new BusinessException("INVOICE_PAYMENT_OVERPAID", "Payment allocation cannot exceed invoice outstanding amount.");
        }
        paidAmount = paidAmount.add(normalizedAmount).setScale(2, java.math.RoundingMode.HALF_UP);
        outstandingAmount = outstandingAmount.subtract(normalizedAmount).setScale(2, java.math.RoundingMode.HALF_UP);
        status = outstandingAmount.signum() == 0 ? InvoiceStatus.PAID : InvoiceStatus.PARTIAL_PAID;
    }

    public void reversePayment(BigDecimal amount) {
        BigDecimal normalizedAmount = requirePositive(amount, "INVOICE_PAYMENT_REVERSAL_AMOUNT_REQUIRED", "Invoice payment reversal amount is required.");
        if (status != InvoiceStatus.PAID && status != InvoiceStatus.PARTIAL_PAID) {
            throw new BusinessException("INVOICE_PAYMENT_REVERSAL_STATUS_INVALID", "Payment reversal can only be applied to paid or partial paid invoice.");
        }
        if (normalizedAmount.compareTo(paidAmount) > 0) {
            throw new BusinessException("INVOICE_PAYMENT_REVERSAL_OVER_AMOUNT", "Payment reversal cannot exceed invoice paid amount.");
        }
        paidAmount = paidAmount.subtract(normalizedAmount).setScale(2, java.math.RoundingMode.HALF_UP);
        outstandingAmount = outstandingAmount.add(normalizedAmount).setScale(2, java.math.RoundingMode.HALF_UP);
        if (paidAmount.signum() == 0) {
            status = InvoiceStatus.ISSUED;
        } else {
            status = outstandingAmount.signum() == 0 ? InvoiceStatus.PAID : InvoiceStatus.PARTIAL_PAID;
        }
    }

    public void voidUnpaid(Instant voidedAt, UUID voidJournalEntryId) {
        if (voidedAt == null) {
            throw new BusinessException("INVOICE_VOIDED_AT_REQUIRED", "Invoice void timestamp is required.");
        }
        if (voidJournalEntryId == null) {
            throw new BusinessException("INVOICE_VOID_JOURNAL_REQUIRED", "Invoice void journal is required.");
        }
        if (status != InvoiceStatus.ISSUED) {
            throw new BusinessException("INVOICE_VOID_STATUS_INVALID", "Only issued unpaid invoice can be voided.");
        }
        if (issueJournalEntryId == null) {
            throw new BusinessException("INVOICE_VOID_ISSUE_JOURNAL_REQUIRED", "Issued invoice must have journal trace before void.");
        }
        if (paidAmount.signum() > 0) {
            throw new BusinessException("INVOICE_VOID_PAID_INVALID", "Paid or partial paid invoice must be reversed before void.");
        }
        this.status = InvoiceStatus.VOID;
        this.outstandingAmount = BigDecimal.ZERO.setScale(2);
        this.voidedAt = voidedAt;
        this.voidJournalEntryId = voidJournalEntryId;
    }

    private static String require(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String code, String message) {
        if (value == null) {
            throw new BusinessException(code, message);
        }
        if (value.signum() < 0) {
            throw new BusinessException("INVOICE_AMOUNT_NEGATIVE", "Invoice amount cannot be negative.");
        }
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static BigDecimal requirePositive(BigDecimal value, String code, String message) {
        BigDecimal normalized = requireNonNegative(value, code, message);
        if (normalized.signum() <= 0) {
            throw new BusinessException("INVOICE_PAYMENT_AMOUNT_INVALID", "Invoice payment amount must be greater than zero.");
        }
        return normalized;
    }

    public UUID getBillingBatchId() {
        return billingBatchId;
    }

    public UUID getConnectionId() {
        return connectionId;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public String getPeriod() {
        return period;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getPenaltyAmount() {
        return penaltyAmount;
    }

    public BigDecimal getUsageCharge() { return usageCharge; }

    public BigDecimal getFixedCharge() { return fixedCharge; }

    public BigDecimal getLevyCharge() { return levyCharge; }

    public BigDecimal getAdminCharge() { return adminCharge; }

    public BigDecimal getWasteCharge() { return wasteCharge; }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public BigDecimal getOutstandingAmount() {
        return outstandingAmount;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public UUID getIssueJournalEntryId() {
        return issueJournalEntryId;
    }

    public Instant getVoidedAt() {
        return voidedAt;
    }

    public UUID getVoidJournalEntryId() {
        return voidJournalEntryId;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }
}
