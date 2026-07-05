package id.pdam.sia.payment.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SettleCounterPaymentRequest(
        @Size(max = 128) String externalReference,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotNull Instant paidAt,
        @NotEmpty List<@Valid PaymentAllocationRequest> allocations,
        @NotBlank @Size(max = 500) String reason
) {
}
