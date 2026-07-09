package id.pdam.sia.billing.web;

import id.pdam.sia.billing.application.InvoiceDocumentConnection;

import java.util.UUID;

public record InvoiceDocumentConnectionResponse(
        UUID id,
        String connectionNumber,
        String meterNumber
) {
    public static InvoiceDocumentConnectionResponse from(InvoiceDocumentConnection connection) {
        return new InvoiceDocumentConnectionResponse(
                connection.id(),
                connection.connectionNumber(),
                connection.meterNumber()
        );
    }
}
