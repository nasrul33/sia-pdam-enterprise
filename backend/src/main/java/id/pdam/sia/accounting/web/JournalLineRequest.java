package id.pdam.sia.accounting.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record JournalLineRequest(
        @NotNull
        UUID accountId,

        @NotNull
        @DecimalMin(value = "0.00")
        @Digits(integer = 17, fraction = 2)
        BigDecimal debit,

        @NotNull
        @DecimalMin(value = "0.00")
        @Digits(integer = 17, fraction = 2)
        BigDecimal credit,

        @Size(max = 255)
        String description
) {
}
