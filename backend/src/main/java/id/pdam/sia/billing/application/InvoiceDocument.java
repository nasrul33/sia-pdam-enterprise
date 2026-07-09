package id.pdam.sia.billing.application;

import id.pdam.sia.billing.domain.InvoiceLineType;
import id.pdam.sia.billing.domain.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InvoiceDocument(
        UUID invoiceId,
        String invoiceNumber,
        String period,
        InvoiceStatus status,
        InvoiceDocumentCustomer customer,
        InvoiceDocumentConnection connection,
        List<InvoiceDocumentLine> lines,
        BigDecimal subtotal,
        BigDecimal penaltyAmount,
        BigDecimal paidAmount,
        BigDecimal outstandingAmount,
        LocalDate dueDate,
        Instant issuedAt,
        UUID issueJournalEntryId,
        Instant voidedAt,
        UUID voidJournalEntryId
) {
}
