package id.pdam.sia.payment.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentAllocationRequest(
        @NotNull UUID invoiceId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount
) {
}
