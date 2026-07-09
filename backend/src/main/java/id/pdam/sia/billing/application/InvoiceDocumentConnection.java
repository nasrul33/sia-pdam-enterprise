package id.pdam.sia.billing.application;

import java.util.UUID;

public record InvoiceDocumentConnection(
        UUID id,
        String connectionNumber,
        String meterNumber
) {
}
