package id.pdam.sia.accounting.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateJournalRequest(
        @NotBlank
        @Size(max = 64)
        String journalNumber,

        @NotNull
        UUID accountingPeriodId,

        @NotBlank
        @Size(max = 255)
        String description,

        @NotEmpty
        @Size(min = 2, max = 100)
        List<@Valid JournalLineRequest> lines,

        @NotBlank
        @Size(max = 500)
        String reason
) {
}
