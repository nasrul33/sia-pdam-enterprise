package id.pdam.sia.accounting.application;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentSettlementPostingCommand(
        String paymentNumber,
        UUID paymentId,
        String period,
        BigDecimal amount,
        UUID cashAccountId,
        UUID receivableAccountId,
        String reason
) {
}
