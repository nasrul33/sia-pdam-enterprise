package id.pdam.sia.metering.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateMeterReadingRequest(
        @NotNull UUID connectionId,
        @NotNull UUID routeId,
        @NotBlank @Pattern(regexp = "^\\d{4}-\\d{2}$") String period,
        @NotNull @DecimalMin("0.000") @Digits(integer = 11, fraction = 3) BigDecimal previousReading,
        @NotNull @DecimalMin("0.000") @Digits(integer = 11, fraction = 3) BigDecimal currentReading,
        @NotNull Instant readAt,
        UUID readerId,
        boolean anomalyFlag,
        @Size(max = 2000) String anomalyReason,
        @NotBlank @Size(max = 500) String reason
) {
}
