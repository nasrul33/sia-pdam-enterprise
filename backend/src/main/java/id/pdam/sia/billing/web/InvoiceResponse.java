package id.pdam.sia.billing.web;

import id.pdam.sia.billing.domain.Invoice;
import id.pdam.sia.billing.domain.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceResponse(
        UUID id,
        UUID billingBatchId,
        UUID connectionId,
        String invoiceNumber,
        String period,
        InvoiceStatus status,
        BigDecimal subtotal,
        BigDecimal penaltyAmount,
        BigDecimal paidAmount,
        BigDecimal outstandingAmount,
        Instant issuedAt,
        UUID issueJournalEntryId,
        LocalDate dueDate,
        Instant createdAt,
        Instant updatedAt
) {
    public static InvoiceResponse from(Invoice invoice) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getBillingBatchId(),
                invoice.getConnectionId(),
                invoice.getInvoiceNumber(),
                invoice.getPeriod(),
                invoice.getStatus(),
                invoice.getSubtotal(),
                invoice.getPenaltyAmount(),
                invoice.getPaidAmount(),
                invoice.getOutstandingAmount(),
                invoice.getIssuedAt(),
                invoice.getIssueJournalEntryId(),
                invoice.getDueDate(),
                invoice.getCreatedAt(),
                invoice.getUpdatedAt()
        );
    }
}
