package id.pdam.sia.customer.web;

import id.pdam.sia.customer.domain.CustomerAddress;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CustomerAddressResponse(
        UUID id,
        String addressLine,
        String areaCode,
        BigDecimal latitude,
        BigDecimal longitude,
        Instant createdAt,
        Instant updatedAt
) {
    public static CustomerAddressResponse from(CustomerAddress address) {
        return new CustomerAddressResponse(
                address.getId(),
                address.getAddressLine(),
                address.getAreaCode(),
                address.getLatitude(),
                address.getLongitude(),
                address.getCreatedAt(),
                address.getUpdatedAt()
        );
    }
}
