package id.pdam.sia.billing.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateTariffVersionRequest(
        @NotNull UUID tariffGroupId,
        @NotNull LocalDate effectiveDate,
        @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal fixedCharge,
        @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal levyCharge,
        @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal adminCharge,
        @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal wasteCharge,
        @DecimalMin("0.000000") @DecimalMax("1.000000") @Digits(integer = 1, fraction = 6) BigDecimal penaltyRate,
        @NotBlank @Size(max = 500) String reason
) {
    public CreateTariffVersionRequest(UUID tariffGroupId, LocalDate effectiveDate, String reason) {
        this(tariffGroupId, effectiveDate, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, reason);
    }
}
