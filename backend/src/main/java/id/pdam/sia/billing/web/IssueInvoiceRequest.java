package id.pdam.sia.billing.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record IssueInvoiceRequest(
        @NotNull UUID receivableAccountId,
        @NotNull UUID revenueAccountId,
        @NotBlank @Size(max = 500) String reason
) {
}
