package id.pdam.sia.payment.domain;

import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_receipts")
public class PaymentReceipt extends BaseEntity {
    @Column(nullable = false, unique = true)
    private UUID paymentId;

    @Column(nullable = false, unique = true, length = 64)
    private String receiptNumber;

    @Column(nullable = false)
    private Instant issuedAt;

    protected PaymentReceipt() {
    }

    public PaymentReceipt(UUID paymentId, String receiptNumber, Instant issuedAt) {
        if (paymentId == null) {
            throw new BusinessException("PAYMENT_RECEIPT_PAYMENT_REQUIRED", "Payment receipt payment is required.");
        }
        if (issuedAt == null) {
            throw new BusinessException("PAYMENT_RECEIPT_ISSUED_AT_REQUIRED", "Payment receipt issued timestamp is required.");
        }
        this.paymentId = paymentId;
        this.receiptNumber = require(receiptNumber, "PAYMENT_RECEIPT_NUMBER_REQUIRED", "Payment receipt number is required.");
        this.issuedAt = issuedAt;
    }

    private static String require(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public String getReceiptNumber() {
        return receiptNumber;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }
}
