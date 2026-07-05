package id.pdam.sia.payment.web;

import id.pdam.sia.payment.domain.PaymentWebhookEvent;
import id.pdam.sia.payment.domain.PaymentWebhookStatus;

import java.time.Instant;
import java.util.UUID;

public record PaymentWebhookResponse(
        UUID id,
        String provider,
        String externalReference,
        String idempotencyKey,
        PaymentWebhookStatus status,
        Instant receivedAt,
        Instant processedAt,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
    public static PaymentWebhookResponse from(PaymentWebhookEvent event) {
        return new PaymentWebhookResponse(
                event.getId(),
                event.getProvider(),
                event.getExternalReference(),
                event.getIdempotencyKey(),
                event.getStatus(),
                event.getReceivedAt(),
                event.getProcessedAt(),
                event.getErrorMessage(),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }
}
