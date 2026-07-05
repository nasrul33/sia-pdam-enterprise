package id.pdam.sia.metering.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMeterRouteRequest(
        @NotBlank @Size(max = 64) String routeCode,
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 64) String areaCode,
        @NotBlank @Size(max = 500) String reason
) {
}
