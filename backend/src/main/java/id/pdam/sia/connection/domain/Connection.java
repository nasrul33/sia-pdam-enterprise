package id.pdam.sia.connection.domain;

import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "connections")
public class Connection extends BaseEntity {
    @Column(nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private UUID tariffGroupId;

    @Column(nullable = false, unique = true, length = 64)
    private String connectionNumber;

    @Column(nullable = false, length = 64)
    private String meterNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ConnectionStatus status;

    private LocalDate installedAt;

    protected Connection() {
    }

    public Connection(UUID customerId, UUID tariffGroupId, String connectionNumber, String meterNumber, LocalDate installedAt) {
        if (customerId == null) {
            throw new BusinessException("CONNECTION_CUSTOMER_REQUIRED", "Connection customer is required.");
        }
        if (tariffGroupId == null) {
            throw new BusinessException("CONNECTION_TARIFF_GROUP_REQUIRED", "Connection tariff group is required.");
        }
        this.customerId = customerId;
        this.tariffGroupId = tariffGroupId;
        this.connectionNumber = require(connectionNumber, "CONNECTION_NUMBER_REQUIRED", "Connection number is required.");
        this.meterNumber = require(meterNumber, "METER_NUMBER_REQUIRED", "Meter number is required.");
        this.installedAt = installedAt;
        this.status = ConnectionStatus.DRAFT;
    }

    public void activate() {
        if (status != ConnectionStatus.DRAFT && status != ConnectionStatus.SUSPENDED) {
            throw new BusinessException("CONNECTION_ACTIVATE_INVALID", "Only draft or suspended connection can be activated.");
        }
        status = ConnectionStatus.ACTIVE;
    }

    public void suspend() {
        if (status != ConnectionStatus.ACTIVE) {
            throw new BusinessException("CONNECTION_SUSPEND_INVALID", "Only active connection can be suspended.");
        }
        status = ConnectionStatus.SUSPENDED;
    }

    public void terminate() {
        if (status == ConnectionStatus.TERMINATED) {
            return;
        }
        if (status == ConnectionStatus.DRAFT) {
            throw new BusinessException("CONNECTION_TERMINATE_INVALID", "Draft connection cannot be terminated.");
        }
        status = ConnectionStatus.TERMINATED;
    }

    private static String require(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getTariffGroupId() {
        return tariffGroupId;
    }

    public String getConnectionNumber() {
        return connectionNumber;
    }

    public String getMeterNumber() {
        return meterNumber;
    }

    public ConnectionStatus getStatus() {
        return status;
    }

    public LocalDate getInstalledAt() {
        return installedAt;
    }
}
