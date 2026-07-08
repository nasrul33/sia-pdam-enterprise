package id.pdam.sia.payment.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignOffPaymentReconciliationSessionRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
