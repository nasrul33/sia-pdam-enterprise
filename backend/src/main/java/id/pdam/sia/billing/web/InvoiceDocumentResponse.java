package id.pdam.sia.billing.web;

import id.pdam.sia.billing.application.InvoiceDocument;
import id.pdam.sia.billing.domain.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InvoiceDocumentResponse(
        UUID invoiceId,
        String invoiceNumber,
        String period,
        InvoiceStatus status,
        InvoiceDocumentCustomerResponse customer,
        InvoiceDocumentConnectionResponse connection,
        List<InvoiceDocumentLineResponse> lines,
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
    public static InvoiceDocumentResponse from(InvoiceDocument document) {
        return new InvoiceDocumentResponse(
                document.invoiceId(),
                document.invoiceNumber(),
                document.period(),
                document.status(),
                InvoiceDocumentCustomerResponse.from(document.customer()),
                InvoiceDocumentConnectionResponse.from(document.connection()),
                document.lines().stream().map(InvoiceDocumentLineResponse::from).toList(),
                document.subtotal(),
                document.penaltyAmount(),
                document.paidAmount(),
                document.outstandingAmount(),
                document.dueDate(),
                document.issuedAt(),
                document.issueJournalEntryId(),
                document.voidedAt(),
                document.voidJournalEntryId()
        );
    }
}
