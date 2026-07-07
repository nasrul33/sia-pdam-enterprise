package id.pdam.sia.payment.web;

import id.pdam.sia.payment.application.BankStatementRowCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreatePaymentReconciliationSessionRequest(
        @Size(max = 255) String sourceFilename,
        @Size(max = 128) String bankAccountReference,
        @NotEmpty @Size(max = 200) List<PaymentReconciliationMatchRequest.@Valid Row> rows
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
}
