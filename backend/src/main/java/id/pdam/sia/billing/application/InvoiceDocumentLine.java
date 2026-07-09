package id.pdam.sia.billing.application;

import id.pdam.sia.billing.domain.InvoiceLineType;

import java.math.BigDecimal;

public record InvoiceDocumentLine(
        InvoiceLineType lineType,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal amount
) {
}
