package id.pdam.sia.customer.domain;

import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.persistence.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "customers")
public class Customer extends BaseEntity {
    @Column(nullable = false, unique = true, length = 64)
    private String customerNumber;

    @Column(nullable = false, length = 255)
    private String fullName;

    @Column(length = 64)
    private String identityNumber;

    @Column(length = 64)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CustomerStatus status;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CustomerAddress> addresses = new ArrayList<>();

    protected Customer() {
    }

    public Customer(String customerNumber, String fullName, String identityNumber, String phoneNumber) {
        this.customerNumber = require(customerNumber, "CUSTOMER_NUMBER_REQUIRED", "Customer number is required.");
        this.fullName = require(fullName, "CUSTOMER_NAME_REQUIRED", "Customer name is required.");
        this.identityNumber = normalize(identityNumber);
        this.phoneNumber = normalize(phoneNumber);
        this.status = CustomerStatus.ACTIVE;
    }

    public void addAddress(String addressLine, String areaCode, java.math.BigDecimal latitude, java.math.BigDecimal longitude) {
        addresses.add(new CustomerAddress(this, addressLine, areaCode, latitude, longitude));
    }

    public void deactivate() {
        if (status != CustomerStatus.ACTIVE) {
            throw new BusinessException("CUSTOMER_NOT_ACTIVE", "Only active customer can be deactivated.");
        }
        status = CustomerStatus.INACTIVE;
    }

    public void blacklist() {
        if (status == CustomerStatus.BLACKLISTED) {
            return;
        }
        status = CustomerStatus.BLACKLISTED;
    }

    private static String require(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public String getCustomerNumber() {
        return customerNumber;
    }

    public String getFullName() {
        return fullName;
    }

    public String getIdentityNumber() {
        return identityNumber;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public CustomerStatus getStatus() {
        return status;
    }

    public List<CustomerAddress> getAddresses() {
        return Collections.unmodifiableList(addresses);
    }
}
