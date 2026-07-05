package id.pdam.sia.billing.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateTariffBlockRequest(
        @Min(1) int blockOrder,
        @NotNull @DecimalMin("0.000") @Digits(integer = 11, fraction = 3) BigDecimal minM3,
        @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal maxM3,
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal pricePerM3,
        @NotBlank @Size(max = 500) String reason
) {
}
