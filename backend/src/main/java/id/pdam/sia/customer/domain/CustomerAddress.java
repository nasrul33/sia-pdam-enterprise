package id.pdam.sia.customer.domain;

import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "customer_addresses")
public class CustomerAddress extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String addressLine;

    @Column(nullable = false, length = 64)
    private String areaCode;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    protected CustomerAddress() {
    }

    CustomerAddress(Customer customer, String addressLine, String areaCode, BigDecimal latitude, BigDecimal longitude) {
        if (customer == null) {
            throw new BusinessException("CUSTOMER_REQUIRED", "Customer is required.");
        }
        this.customer = customer;
        this.addressLine = require(addressLine, "CUSTOMER_ADDRESS_REQUIRED", "Customer address is required.");
        this.areaCode = require(areaCode, "CUSTOMER_AREA_REQUIRED", "Customer area code is required.");
        this.latitude = latitude;
        this.longitude = longitude;
    }

    private static String require(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    public String getAddressLine() {
        return addressLine;
    }

    public String getAreaCode() {
        return areaCode;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }
}
