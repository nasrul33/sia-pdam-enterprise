package id.pdam.sia.billing.web;

import id.pdam.sia.billing.application.InvoiceDocumentLine;
import id.pdam.sia.billing.domain.InvoiceLineType;

import java.math.BigDecimal;

public record InvoiceDocumentLineResponse(
        InvoiceLineType lineType,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal amount
) {
    public static InvoiceDocumentLineResponse from(InvoiceDocumentLine line) {
        return new InvoiceDocumentLineResponse(
                line.lineType(),
                line.description(),
                line.quantity(),
                line.unitPrice(),
                line.amount()
        );
    }
}
