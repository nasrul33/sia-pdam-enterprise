package id.pdam.sia.receivable.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CollectionActionWorkflowRequest(
        @Size(max = 1000) String notes,
        @NotBlank @Size(max = 500) String reason
) {
}
