package id.pdam.sia.payment.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaymentWebhookRequest(
        @NotBlank @Size(max = 64) String provider,
        @NotBlank @Size(max = 128) String externalReference,
        @NotBlank @Size(max = 128) String idempotencyKey,
        @NotBlank String payload
) {
}
