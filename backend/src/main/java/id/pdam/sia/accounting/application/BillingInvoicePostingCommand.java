package id.pdam.sia.accounting.application;

import java.math.BigDecimal;
import java.util.UUID;

public record BillingInvoicePostingCommand(
        String invoiceNumber,
        UUID invoiceId,
        String period,
        BigDecimal amount,
        BigDecimal waterRevenueAmount,
        BigDecimal nonAirRevenueAmount,
        BigDecimal penaltyRevenueAmount,
        UUID receivableAccountId,
        UUID revenueAccountId,
        UUID nonAirRevenueAccountId,
        UUID penaltyRevenueAccountId,
        String reason
) {
    public BillingInvoicePostingCommand(
            String invoiceNumber, UUID invoiceId, String period, BigDecimal amount,
            UUID receivableAccountId, UUID revenueAccountId, String reason
    ) {
        this(invoiceNumber, invoiceId, period, amount, amount, BigDecimal.ZERO, BigDecimal.ZERO,
                receivableAccountId, revenueAccountId, null, null, reason);
    }
}
