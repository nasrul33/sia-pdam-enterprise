package id.pdam.sia.customer.web;

import id.pdam.sia.customer.domain.Customer;
import id.pdam.sia.customer.domain.CustomerStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String customerNumber,
        String fullName,
        String identityNumber,
        String phoneNumber,
        CustomerStatus status,
        List<CustomerAddressResponse> addresses,
        Instant createdAt,
        Instant updatedAt
) {
    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getCustomerNumber(),
                customer.getFullName(),
                customer.getIdentityNumber(),
                customer.getPhoneNumber(),
                customer.getStatus(),
                customer.getAddresses().stream().map(CustomerAddressResponse::from).toList(),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }
}
