package id.pdam.sia.billing.application;

import java.util.UUID;

public record InvoiceDocumentCustomer(
        UUID id,
        String customerNumber,
        String fullName,
        String addressLine,
        String areaCode,
        String phoneNumber
) {
}
