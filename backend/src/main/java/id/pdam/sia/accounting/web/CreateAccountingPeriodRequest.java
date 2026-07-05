package id.pdam.sia.accounting.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAccountingPeriodRequest(
        @NotBlank
        @Pattern(regexp = "^\\d{4}-\\d{2}$")
        String period,

        @NotBlank
        @Size(max = 500)
        String reason
) {
}
