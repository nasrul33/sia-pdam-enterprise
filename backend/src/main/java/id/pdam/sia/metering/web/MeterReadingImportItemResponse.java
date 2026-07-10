package id.pdam.sia.metering.web;

import id.pdam.sia.metering.domain.MeterReadingImportItem;
import id.pdam.sia.metering.domain.MeterReadingImportItemStatus;

import java.util.UUID;

public record MeterReadingImportItemResponse(
        int rowNumber,
        UUID connectionId,
        UUID readingId,
        MeterReadingImportItemStatus status,
        String code,
        String message
) {
    public static MeterReadingImportItemResponse from(MeterReadingImportItem item) {
        return new MeterReadingImportItemResponse(
                item.getRowNumber(),
                item.getConnectionId(),
                item.getReadingId(),
                item.getStatus(),
                item.getCode(),
                item.getMessage()
        );
    }
}
