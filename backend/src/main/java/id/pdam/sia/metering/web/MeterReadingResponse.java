package id.pdam.sia.metering.web;

import id.pdam.sia.metering.domain.MeterReading;
import id.pdam.sia.metering.domain.MeterReadingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MeterReadingResponse(
        UUID id,
        UUID connectionId,
        UUID routeId,
        String period,
        BigDecimal previousReading,
        BigDecimal currentReading,
        BigDecimal usageM3,
        MeterReadingStatus status,
        Instant readAt,
        UUID readerId,
        boolean anomalyFlag,
        String anomalyReason,
        UUID importBatchId,
        String sourceDeviceId,
        Integer sourceRowNumber,
        Instant lockedAt,
        String lockedBy,
        List<String> availableActions,
        Instant createdAt,
        Instant updatedAt
) {
    public static MeterReadingResponse from(MeterReading reading) {
        return new MeterReadingResponse(
                reading.getId(),
                reading.getConnectionId(),
                reading.getRouteId(),
                reading.getPeriod(),
                reading.getPreviousReading(),
                reading.getCurrentReading(),
                reading.getUsageM3(),
                reading.getStatus(),
                reading.getReadAt(),
                reading.getReaderId(),
                reading.isAnomalyFlag(),
                reading.getAnomalyReason(),
                reading.getImportBatchId(),
                reading.getSourceDeviceId(),
                reading.getSourceRowNumber(),
                reading.getLockedAt(),
                reading.getLockedBy(),
                availableActions(reading),
                reading.getCreatedAt(),
                reading.getUpdatedAt()
        );
    }

    private static List<String> availableActions(MeterReading reading) {
        return switch (reading.getStatus()) {
            case DRAFT, REJECTED -> List.of("submit");
            case SUBMITTED -> List.of("verify", "reject");
            case VERIFIED -> List.of("lock");
            case LOCKED -> List.of();
        };
    }
}
