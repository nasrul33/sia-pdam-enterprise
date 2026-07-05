package id.pdam.sia.payment.domain;

import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Entity
@Table(name = "payment_allocations")
public class PaymentAllocation extends BaseEntity {
    @Column(nullable = false)
    private UUID paymentId;

    @Column(nullable = false)
    private UUID invoiceId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    protected PaymentAllocation() {
    }

    public PaymentAllocation(UUID paymentId, UUID invoiceId, BigDecimal amount) {
        if (paymentId == null) {
            throw new BusinessException("PAYMENT_ALLOCATION_PAYMENT_REQUIRED", "Payment allocation payment is required.");
        }
        if (invoiceId == null) {
            throw new BusinessException("PAYMENT_ALLOCATION_INVOICE_REQUIRED", "Payment allocation invoice is required.");
        }
        this.paymentId = paymentId;
        this.invoiceId = invoiceId;
        this.amount = requirePositive(amount);
    }

    private static BigDecimal requirePositive(BigDecimal value) {
        if (value == null) {
            throw new BusinessException("PAYMENT_ALLOCATION_AMOUNT_REQUIRED", "Payment allocation amount is required.");
        }
        BigDecimal normalized = value.setScale(2, RoundingMode.HALF_UP);
        if (normalized.signum() <= 0) {
            throw new BusinessException("PAYMENT_ALLOCATION_AMOUNT_INVALID", "Payment allocation amount must be greater than zero.");
        }
        return normalized;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
