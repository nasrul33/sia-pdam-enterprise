package id.pdam.sia.billing.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CalculateTariffRequest(
        @NotNull UUID tariffGroupId,
        @NotNull LocalDate billingDate,
        @NotNull @DecimalMin("0.000") @Digits(integer = 11, fraction = 3) BigDecimal usageM3
) {
}
