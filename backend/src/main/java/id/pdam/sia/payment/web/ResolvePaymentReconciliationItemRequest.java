package id.pdam.sia.payment.web;

import id.pdam.sia.payment.domain.PaymentReconciliationResolutionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResolvePaymentReconciliationItemRequest(
        @NotNull PaymentReconciliationResolutionStatus resolutionStatus,
        @NotBlank @Size(max = 500) String reason
) {
}
