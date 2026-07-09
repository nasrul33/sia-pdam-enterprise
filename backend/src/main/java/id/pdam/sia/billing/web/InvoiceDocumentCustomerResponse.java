package id.pdam.sia.billing.web;

import id.pdam.sia.billing.application.InvoiceDocumentCustomer;

import java.util.UUID;

public record InvoiceDocumentCustomerResponse(
        UUID id,
        String customerNumber,
        String fullName,
        String addressLine,
        String areaCode,
        String phoneNumber
) {
    public static InvoiceDocumentCustomerResponse from(InvoiceDocumentCustomer customer) {
        return new InvoiceDocumentCustomerResponse(
                customer.id(),
                customer.customerNumber(),
                customer.fullName(),
                customer.addressLine(),
                customer.areaCode(),
                customer.phoneNumber()
        );
    }
}
