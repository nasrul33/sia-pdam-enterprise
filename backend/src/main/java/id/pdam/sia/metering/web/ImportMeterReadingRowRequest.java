package id.pdam.sia.metering.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ImportMeterReadingRowRequest(
        @NotNull UUID connectionId,
        @NotNull @DecimalMin("0.000") @Digits(integer = 11, fraction = 3) BigDecimal previousReading,
        @NotNull @DecimalMin("0.000") @Digits(integer = 11, fraction = 3) BigDecimal currentReading,
        @NotNull Instant readAt,
        UUID readerId,
        boolean anomalyFlag,
        @Size(max = 2000) String anomalyReason
) {
}
