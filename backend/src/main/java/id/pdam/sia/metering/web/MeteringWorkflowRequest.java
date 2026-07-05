package id.pdam.sia.metering.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MeteringWorkflowRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
