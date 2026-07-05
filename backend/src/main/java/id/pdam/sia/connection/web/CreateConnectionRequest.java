package id.pdam.sia.connection.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateConnectionRequest(
        @NotNull
        UUID customerId,

        @NotNull
        UUID tariffGroupId,

        @NotBlank
        @Size(max = 64)
        String connectionNumber,

        @NotBlank
        @Size(max = 64)
        String meterNumber,

        LocalDate installedAt,

        @NotBlank
        @Size(max = 500)
        String reason
) {
}
