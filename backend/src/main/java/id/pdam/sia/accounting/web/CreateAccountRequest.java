package id.pdam.sia.accounting.web;

import id.pdam.sia.accounting.domain.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
        @NotBlank
        @Size(max = 64)
        String code,

        @NotBlank
        @Size(max = 255)
        String name,

        @NotNull
        AccountType type,

        @NotBlank
        @Size(max = 500)
        String reason
) {
}
