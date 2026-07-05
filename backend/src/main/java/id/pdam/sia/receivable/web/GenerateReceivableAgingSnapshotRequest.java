package id.pdam.sia.receivable.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record GenerateReceivableAgingSnapshotRequest(
        @NotBlank @Pattern(regexp = "^\\d{4}-\\d{2}$") String period,
        @NotNull LocalDate asOfDate,
        @NotBlank @Size(max = 500) String reason
) {
}
