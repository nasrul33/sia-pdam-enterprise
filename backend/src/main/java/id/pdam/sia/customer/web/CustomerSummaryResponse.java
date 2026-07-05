package id.pdam.sia.customer.web;

import id.pdam.sia.customer.domain.Customer;
import id.pdam.sia.customer.domain.CustomerStatus;

import java.time.Instant;
import java.util.UUID;

public record CustomerSummaryResponse(
        UUID id,
        String customerNumber,
        String fullName,
        String phoneNumber,
        CustomerStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static CustomerSummaryResponse from(Customer customer) {
        return new CustomerSummaryResponse(
                customer.getId(),
                customer.getCustomerNumber(),
                customer.getFullName(),
                customer.getPhoneNumber(),
                customer.getStatus(),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }
}
