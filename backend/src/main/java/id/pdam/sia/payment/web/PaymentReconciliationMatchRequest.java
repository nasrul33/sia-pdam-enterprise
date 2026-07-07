package id.pdam.sia.payment.web;

import id.pdam.sia.payment.application.BankStatementRowCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PaymentReconciliationMatchRequest(
        @NotEmpty @Size(max = 200) List<@Valid Row> rows
) {
    public List<BankStatementRowCommand> toCommands() {
        return rows.stream()
                .map(row -> new BankStatementRowCommand(
                        row.statementReference(),
                        row.amount(),
                        row.transactedAt(),
                        row.channel()
                ))
                .toList();
    }

    public record Row(
            @NotBlank @Size(max = 128) String statementReference,
            @NotNull @Positive BigDecimal amount,
            @NotNull Instant transactedAt,
            @Size(max = 64) String channel
    ) {
    }
}
