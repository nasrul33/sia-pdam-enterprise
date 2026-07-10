package id.pdam.sia.accounting.domain;

import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "suppliers")
public class Supplier extends BaseEntity {
    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 128)
    private String contactName;

    @Column(length = 64)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SupplierStatus status;

    protected Supplier() {
    }

    public Supplier(String code, String name, String contactName, String phoneNumber) {
        this.code = require(code, "SUPPLIER_CODE_REQUIRED", "Supplier code is required.");
        this.name = require(name, "SUPPLIER_NAME_REQUIRED", "Supplier name is required.");
        this.contactName = normalize(contactName);
        this.phoneNumber = normalize(phoneNumber);
        this.status = SupplierStatus.ACTIVE;
    }

    public void update(String name, String contactName, String phoneNumber, SupplierStatus status) {
        this.name = require(name, "SUPPLIER_NAME_REQUIRED", "Supplier name is required.");
        this.contactName = normalize(contactName);
        this.phoneNumber = normalize(phoneNumber);
        if (status == null) {
            throw new BusinessException("SUPPLIER_STATUS_REQUIRED", "Supplier status is required.");
        }
        this.status = status;
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

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getContactName() {
        return contactName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public SupplierStatus getStatus() {
        return status;
    }
}
