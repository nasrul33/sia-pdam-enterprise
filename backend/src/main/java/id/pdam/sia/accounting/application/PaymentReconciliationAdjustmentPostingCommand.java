package id.pdam.sia.accounting.application;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentReconciliationAdjustmentPostingCommand(
        UUID reconciliationItemId,
        String sessionNumber,
        int rowNumber,
        String period,
        BigDecimal amount,
        UUID debitAccountId,
        UUID creditAccountId,
        String reason
) {
}
