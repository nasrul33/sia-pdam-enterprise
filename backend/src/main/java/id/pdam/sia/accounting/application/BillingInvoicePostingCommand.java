package id.pdam.sia.accounting.application;

import java.math.BigDecimal;
import java.util.UUID;

public record BillingInvoicePostingCommand(
        String invoiceNumber,
        UUID invoiceId,
        String period,
        BigDecimal amount,
        UUID receivableAccountId,
        UUID revenueAccountId,
        String reason
) {
}
