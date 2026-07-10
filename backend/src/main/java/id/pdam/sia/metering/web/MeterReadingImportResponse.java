package id.pdam.sia.metering.web;

import id.pdam.sia.metering.application.MeteringApplicationService;
import id.pdam.sia.metering.domain.MeterReadingImportBatch;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MeterReadingImportResponse(
        UUID batchId,
        String sourceDeviceId,
        String sourceBatchReference,
        UUID routeId,
        String period,
        int totalRows,
        int importedRows,
        int skippedRows,
        int invalidRows,
        String importedBy,
        Instant importedAt,
        List<MeterReadingImportItemResponse> items
) {
    public static MeterReadingImportResponse from(MeteringApplicationService.MeterReadingImportResult result) {
        MeterReadingImportBatch batch = result.batch();
        return new MeterReadingImportResponse(
                batch.getId(),
                batch.getSourceDeviceId(),
                batch.getSourceBatchReference(),
                batch.getRouteId(),
                batch.getPeriod(),
                batch.getTotalRows(),
                batch.getImportedRows(),
                batch.getSkippedRows(),
                batch.getInvalidRows(),
                batch.getImportedBy(),
                batch.getImportedAt(),
                result.items().stream().map(MeterReadingImportItemResponse::from).toList()
        );
    }
}
