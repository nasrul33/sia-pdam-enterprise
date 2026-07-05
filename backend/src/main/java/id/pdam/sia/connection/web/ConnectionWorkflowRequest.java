package id.pdam.sia.connection.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConnectionWorkflowRequest(
        @NotBlank
        @Size(max = 500)
        String reason
) {
}
