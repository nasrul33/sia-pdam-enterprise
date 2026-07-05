package id.pdam.sia.payment.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ReversePaymentRequest(
        @NotNull UUID cashAccountId,
        @NotNull UUID receivableAccountId,
        @NotBlank @Size(max = 500) String reason
) {
}
