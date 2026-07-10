package id.pdam.sia.metering.domain;

import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "meter_reading_import_batches")
public class MeterReadingImportBatch extends BaseEntity {
    @Column(nullable = false, length = 128)
    private String sourceDeviceId;

    @Column(nullable = false, unique = true, length = 128)
    private String sourceBatchReference;

    @Column(nullable = false)
    private UUID routeId;

    @Column(nullable = false, length = 7)
    private String period;

    @Column(nullable = false)
    private int totalRows;

    @Column(nullable = false)
    private int importedRows;

    @Column(nullable = false)
    private int skippedRows;

    @Column(nullable = false)
    private int invalidRows;

    @Column(nullable = false, length = 128)
    private String importedBy;

    @Column(nullable = false)
    private Instant importedAt;

    protected MeterReadingImportBatch() {
    }

    public MeterReadingImportBatch(String sourceDeviceId, String sourceBatchReference, UUID routeId, String period, int totalRows, String actor) {
        if (routeId == null) {
            throw new BusinessException("METER_IMPORT_ROUTE_REQUIRED", "Meter reading import route is required.");
        }
        if (totalRows < 1) {
            throw new BusinessException("METER_IMPORT_ROWS_REQUIRED", "Meter reading import rows are required.");
        }
        this.sourceDeviceId = require(sourceDeviceId, "METER_IMPORT_DEVICE_REQUIRED", "Meter reading import device id is required.");
        this.sourceBatchReference = require(
                sourceBatchReference,
                "METER_IMPORT_BATCH_REFERENCE_REQUIRED",
                "Meter reading import batch reference is required."
        );
        this.routeId = routeId;
        this.period = require(period, "METER_IMPORT_PERIOD_REQUIRED", "Meter reading import period is required.");
        this.totalRows = totalRows;
        this.importedBy = require(actor, "METER_IMPORT_ACTOR_REQUIRED", "Meter reading import actor is required.");
        this.importedAt = Instant.now();
    }

    public void markImported() {
        importedRows++;
    }

    public void markSkipped() {
        skippedRows++;
    }

    public void markInvalid() {
        invalidRows++;
    }

    private static String require(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    public String getSourceDeviceId() {
        return sourceDeviceId;
    }

    public String getSourceBatchReference() {
        return sourceBatchReference;
    }

    public UUID getRouteId() {
        return routeId;
    }

    public String getPeriod() {
        return period;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public int getImportedRows() {
        return importedRows;
    }

    public int getSkippedRows() {
        return skippedRows;
    }

    public int getInvalidRows() {
        return invalidRows;
    }

    public String getImportedBy() {
        return importedBy;
    }

    public Instant getImportedAt() {
        return importedAt;
    }
}
