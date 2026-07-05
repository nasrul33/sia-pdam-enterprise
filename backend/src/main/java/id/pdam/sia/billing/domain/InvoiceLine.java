package id.pdam.sia.billing.domain;

import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Entity
@Table(name = "invoice_lines")
public class InvoiceLine extends BaseEntity {
    @Column(nullable = false)
    private UUID invoiceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private InvoiceLineType lineType;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    protected InvoiceLine() {
    }

    public InvoiceLine(
            UUID invoiceId,
            InvoiceLineType lineType,
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal amount
    ) {
        if (invoiceId == null) {
            throw new BusinessException("INVOICE_LINE_INVOICE_REQUIRED", "Invoice line invoice is required.");
        }
        if (lineType == null) {
            throw new BusinessException("INVOICE_LINE_TYPE_REQUIRED", "Invoice line type is required.");
        }
        this.invoiceId = invoiceId;
        this.lineType = lineType;
        this.description = require(description, "INVOICE_LINE_DESCRIPTION_REQUIRED", "Invoice line description is required.");
        this.quantity = requireNonNegative(quantity, "INVOICE_LINE_QUANTITY_REQUIRED", "Invoice line quantity is required.", 3);
        this.unitPrice = requireNonNegative(unitPrice, "INVOICE_LINE_UNIT_PRICE_REQUIRED", "Invoice line unit price is required.", 2);
        this.amount = requireNonNegative(amount, "INVOICE_LINE_AMOUNT_REQUIRED", "Invoice line amount is required.", 2);
    }

    private static String require(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String code, String message, int scale) {
        if (value == null) {
            throw new BusinessException(code, message);
        }
        if (value.signum() < 0) {
            throw new BusinessException("INVOICE_LINE_AMOUNT_NEGATIVE", "Invoice line value cannot be negative.");
        }
        return value.setScale(scale, RoundingMode.HALF_UP);
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public InvoiceLineType getLineType() {
        return lineType;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
