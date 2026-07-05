package id.pdam.sia.connection.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTariffGroupRequest(
        @NotBlank
        @Size(max = 64)
        String code,

        @NotBlank
        @Size(max = 255)
        String name,

        @NotBlank
        @Size(max = 500)
        String reason
) {
}
