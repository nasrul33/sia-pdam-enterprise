package id.pdam.sia.payment.web;

import id.pdam.sia.payment.application.PaymentReconciliationAdjustmentCommand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreatePaymentReconciliationAdjustmentRequest(
        @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}") String period,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotNull UUID debitAccountId,
        @NotNull UUID creditAccountId,
        @NotBlank @Size(max = 500) String reason
) {
    public PaymentReconciliationAdjustmentCommand toCommand() {
        return new PaymentReconciliationAdjustmentCommand(
                period,
                amount,
                debitAccountId,
                creditAccountId,
                reason
        );
    }
}
