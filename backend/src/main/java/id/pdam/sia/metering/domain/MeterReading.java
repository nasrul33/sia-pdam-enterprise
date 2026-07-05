package id.pdam.sia.metering.domain;

import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "meter_readings")
public class MeterReading extends BaseEntity {
    @Column(nullable = false)
    private UUID connectionId;

    @Column(nullable = false)
    private UUID routeId;

    @Column(nullable = false, length = 7)
    private String period;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal previousReading;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal currentReading;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal usageM3;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MeterReadingStatus status;

    @Column(nullable = false)
    private Instant readAt;

    private UUID readerId;

    @Column(nullable = false)
    private boolean anomalyFlag;

    @Column(columnDefinition = "TEXT")
    private String anomalyReason;

    protected MeterReading() {
    }

    public MeterReading(
            UUID connectionId,
            UUID routeId,
            String period,
            BigDecimal previousReading,
            BigDecimal currentReading,
            Instant readAt,
            UUID readerId,
            boolean anomalyFlag,
            String anomalyReason
    ) {
        if (connectionId == null) {
            throw new BusinessException("METER_READING_CONNECTION_REQUIRED", "Meter reading connection is required.");
        }
        if (routeId == null) {
            throw new BusinessException("METER_READING_ROUTE_REQUIRED", "Meter reading route is required.");
        }
        if (readAt == null) {
            throw new BusinessException("METER_READING_READ_AT_REQUIRED", "Meter reading timestamp is required.");
        }
        this.connectionId = connectionId;
        this.routeId = routeId;
        this.period = require(period, "METER_READING_PERIOD_REQUIRED", "Meter reading period is required.");
        this.previousReading = requireNonNegative(previousReading, "METER_READING_PREVIOUS_REQUIRED", "Previous reading is required.");
        this.currentReading = requireNonNegative(currentReading, "METER_READING_CURRENT_REQUIRED", "Current reading is required.");
        this.usageM3 = calculateUsage(this.previousReading, this.currentReading);
        this.status = MeterReadingStatus.DRAFT;
        this.readAt = readAt;
        this.readerId = readerId;
        this.anomalyFlag = anomalyFlag;
        this.anomalyReason = normalizeAnomalyReason(anomalyFlag, anomalyReason);
    }

    public void submit() {
        if (status != MeterReadingStatus.DRAFT && status != MeterReadingStatus.REJECTED) {
            throw new BusinessException("METER_READING_SUBMIT_INVALID", "Only draft or rejected meter reading can be submitted.");
        }
        status = MeterReadingStatus.SUBMITTED;
    }

    public void verify() {
        if (status != MeterReadingStatus.SUBMITTED) {
            throw new BusinessException("METER_READING_VERIFY_INVALID", "Only submitted meter reading can be verified.");
        }
        status = MeterReadingStatus.VERIFIED;
    }

    public void reject() {
        if (status != MeterReadingStatus.SUBMITTED) {
            throw new BusinessException("METER_READING_REJECT_INVALID", "Only submitted meter reading can be rejected.");
        }
        status = MeterReadingStatus.REJECTED;
    }

    private static String require(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String code, String message) {
        if (value == null) {
            throw new BusinessException(code, message);
        }
        if (value.signum() < 0) {
            throw new BusinessException("METER_READING_NEGATIVE", "Meter reading value cannot be negative.");
        }
        return value.stripTrailingZeros();
    }

    private static BigDecimal calculateUsage(BigDecimal previousReading, BigDecimal currentReading) {
        BigDecimal usage = currentReading.subtract(previousReading);
        if (usage.signum() < 0) {
            throw new BusinessException("METER_READING_USAGE_NEGATIVE", "Current reading cannot be lower than previous reading.");
        }
        return usage.stripTrailingZeros();
    }

    private static String normalizeAnomalyReason(boolean anomalyFlag, String anomalyReason) {
        if (!anomalyFlag) {
            return null;
        }
        if (anomalyReason == null || anomalyReason.isBlank()) {
            throw new BusinessException("METER_READING_ANOMALY_REASON_REQUIRED", "Anomaly reason is required when anomaly flag is true.");
        }
        return anomalyReason.trim();
    }

    public UUID getConnectionId() {
        return connectionId;
    }

    public UUID getRouteId() {
        return routeId;
    }

    public String getPeriod() {
        return period;
    }

    public BigDecimal getPreviousReading() {
        return previousReading;
    }

    public BigDecimal getCurrentReading() {
        return currentReading;
    }

    public BigDecimal getUsageM3() {
        return usageM3;
    }

    public MeterReadingStatus getStatus() {
        return status;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public UUID getReaderId() {
        return readerId;
    }

    public boolean isAnomalyFlag() {
        return anomalyFlag;
    }

    public String getAnomalyReason() {
        return anomalyReason;
    }
}
