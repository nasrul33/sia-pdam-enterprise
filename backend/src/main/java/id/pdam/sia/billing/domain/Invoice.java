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
    private BigDecimal penaltyAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal paidAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal outstandingAmount;

    private Instant issuedAt;

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
        this.subtotal = requireNonNegative(subtotal, "INVOICE_SUBTOTAL_REQUIRED", "Invoice subtotal is required.");
        this.penaltyAmount = BigDecimal.ZERO.setScale(2);
        this.paidAmount = BigDecimal.ZERO.setScale(2);
        this.outstandingAmount = this.subtotal;
        this.dueDate = dueDate;
        this.status = InvoiceStatus.DRAFT;
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

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public BigDecimal getOutstandingAmount() {
        return outstandingAmount;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }
}
