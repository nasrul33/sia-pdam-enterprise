package id.pdam.sia.payment.domain;

import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "payment_webhook_events")
public class PaymentWebhookEvent extends BaseEntity {
    @Column(nullable = false, length = 64)
    private String provider;

    @Column(nullable = false, length = 128)
    private String externalReference;

    @Column(nullable = false, length = 128)
    private String idempotencyKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentWebhookStatus status;

    @Column(nullable = false)
    private Instant receivedAt;

    private Instant processedAt;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    protected PaymentWebhookEvent() {
    }

    public PaymentWebhookEvent(String provider, String externalReference, String idempotencyKey, String payload) {
        this.provider = require(provider, "PAYMENT_WEBHOOK_PROVIDER_REQUIRED", "Payment webhook provider is required.");
        this.externalReference = require(externalReference, "PAYMENT_WEBHOOK_REFERENCE_REQUIRED", "Payment webhook external reference is required.");
        this.idempotencyKey = require(idempotencyKey, "PAYMENT_WEBHOOK_IDEMPOTENCY_REQUIRED", "Payment webhook idempotency key is required.");
        this.payload = require(payload, "PAYMENT_WEBHOOK_PAYLOAD_REQUIRED", "Payment webhook payload is required.");
        this.status = PaymentWebhookStatus.RECEIVED;
        this.receivedAt = Instant.now();
    }

    public void markProcessed() {
        status = PaymentWebhookStatus.PROCESSED;
        processedAt = Instant.now();
        errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        status = PaymentWebhookStatus.FAILED;
        processedAt = Instant.now();
        this.errorMessage = require(errorMessage, "PAYMENT_WEBHOOK_ERROR_REQUIRED", "Payment webhook error is required.");
    }

    public void markIgnored(String reason) {
        status = PaymentWebhookStatus.IGNORED;
        processedAt = Instant.now();
        errorMessage = require(reason, "PAYMENT_WEBHOOK_IGNORE_REASON_REQUIRED", "Payment webhook ignore reason is required.");
    }

    private static String require(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    public String getProvider() {
        return provider;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getPayload() {
        return payload;
    }

    public PaymentWebhookStatus getStatus() {
        return status;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
