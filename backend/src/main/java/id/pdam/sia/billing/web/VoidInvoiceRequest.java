package id.pdam.sia.billing.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VoidInvoiceRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
