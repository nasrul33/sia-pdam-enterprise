package id.pdam.sia.billing.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record IssueInvoiceRequest(
        @NotNull UUID receivableAccountId,
        @NotNull UUID revenueAccountId,
        UUID nonAirRevenueAccountId,
        UUID penaltyRevenueAccountId,
        @NotBlank @Size(max = 500) String reason
) {
    public IssueInvoiceRequest(UUID receivableAccountId, UUID revenueAccountId, String reason) {
        this(receivableAccountId, revenueAccountId, null, null, reason);
    }
}
