package id.pdam.sia.accounting.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostJournalRequest(
        @NotBlank
        @Size(max = 500)
        String reason
) {
}
