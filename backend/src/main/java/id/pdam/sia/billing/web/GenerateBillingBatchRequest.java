package id.pdam.sia.billing.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record GenerateBillingBatchRequest(
        @NotBlank @Pattern(regexp = "^\\d{4}-\\d{2}$") String period,
        @NotBlank @Size(max = 64) String areaCode,
        @NotNull LocalDate billingDate,
        @NotNull LocalDate dueDate,
        @NotBlank @Size(max = 500) String reason
) {
}
