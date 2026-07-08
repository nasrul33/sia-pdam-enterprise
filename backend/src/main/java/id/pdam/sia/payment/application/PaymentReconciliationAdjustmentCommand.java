package id.pdam.sia.payment.application;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentReconciliationAdjustmentCommand(
        String period,
        BigDecimal amount,
        UUID debitAccountId,
        UUID creditAccountId,
        String reason
) {
}
