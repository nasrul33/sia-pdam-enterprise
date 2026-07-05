package id.pdam.sia.billing.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateTariffVersionRequest(
        @NotNull UUID tariffGroupId,
        @NotNull LocalDate effectiveDate,
        @NotBlank @Size(max = 500) String reason
) {
}
