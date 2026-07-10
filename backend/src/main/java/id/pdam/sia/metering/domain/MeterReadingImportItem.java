package id.pdam.sia.metering.domain;

import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "meter_reading_import_items")
public class MeterReadingImportItem extends BaseEntity {
    @Column(nullable = false)
    private UUID batchId;

    @Column(nullable = false)
    private int rowNumber;

    private UUID connectionId;

    private UUID readingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MeterReadingImportItemStatus status;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    protected MeterReadingImportItem() {
    }

    private MeterReadingImportItem(
            UUID batchId,
            int rowNumber,
            UUID connectionId,
            UUID readingId,
            MeterReadingImportItemStatus status,
            String code,
            String message
    ) {
        if (batchId == null) {
            throw new BusinessException("METER_IMPORT_ITEM_BATCH_REQUIRED", "Meter reading import item batch is required.");
        }
        if (rowNumber < 1) {
            throw new BusinessException("METER_IMPORT_ITEM_ROW_INVALID", "Meter reading import item row number must be greater than zero.");
        }
        if (status == null) {
            throw new BusinessException("METER_IMPORT_ITEM_STATUS_REQUIRED", "Meter reading import item status is required.");
        }
        this.batchId = batchId;
        this.rowNumber = rowNumber;
        this.connectionId = connectionId;
        this.readingId = readingId;
        this.status = status;
        this.code = require(code, "METER_IMPORT_ITEM_CODE_REQUIRED", "Meter reading import item code is required.");
        this.message = require(message, "METER_IMPORT_ITEM_MESSAGE_REQUIRED", "Meter reading import item message is required.");
    }

    public static MeterReadingImportItem imported(UUID batchId, int rowNumber, UUID connectionId, UUID readingId) {
        return new MeterReadingImportItem(batchId, rowNumber, connectionId, readingId, MeterReadingImportItemStatus.IMPORTED, "IMPORTED", "Imported.");
    }

    public static MeterReadingImportItem skipped(UUID batchId, int rowNumber, UUID connectionId, String code, String message) {
        return new MeterReadingImportItem(batchId, rowNumber, connectionId, null, MeterReadingImportItemStatus.SKIPPED, code, message);
    }

    public static MeterReadingImportItem invalid(UUID batchId, int rowNumber, UUID connectionId, String code, String message) {
        return new MeterReadingImportItem(batchId, rowNumber, connectionId, null, MeterReadingImportItemStatus.INVALID, code, message);
    }

    private static String require(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    public UUID getBatchId() {
        return batchId;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public UUID getConnectionId() {
        return connectionId;
    }

    public UUID getReadingId() {
        return readingId;
    }

    public MeterReadingImportItemStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
