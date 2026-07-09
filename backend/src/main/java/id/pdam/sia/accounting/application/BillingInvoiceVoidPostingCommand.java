package id.pdam.sia.accounting.application;

import java.util.UUID;

public record BillingInvoiceVoidPostingCommand(
        String invoiceNumber,
        UUID invoiceId,
        String period,
        UUID originalJournalEntryId,
        String reason
) {
}
