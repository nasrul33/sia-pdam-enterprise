package id.pdam.sia.metering.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ImportMeterReadingsRequest(
        @NotBlank @Size(max = 128) String sourceDeviceId,
        @NotBlank @Size(max = 128) String sourceBatchReference,
        @NotNull UUID routeId,
        @NotBlank @Pattern(regexp = "^\\d{4}-\\d{2}$") String period,
        @NotEmpty @Size(max = 1000) List<@NotNull @Valid ImportMeterReadingRowRequest> rows,
        @NotBlank @Size(max = 500) String reason
) {
}
