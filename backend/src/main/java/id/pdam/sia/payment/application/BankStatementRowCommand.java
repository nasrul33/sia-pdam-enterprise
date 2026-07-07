package id.pdam.sia.payment.application;

import java.math.BigDecimal;
import java.time.Instant;

public record BankStatementRowCommand(
        String statementReference,
        BigDecimal amount,
        Instant transactedAt,
        String channel
) {
}
