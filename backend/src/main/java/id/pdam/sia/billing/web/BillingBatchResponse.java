package id.pdam.sia.billing.web;

import id.pdam.sia.billing.domain.BillingBatch;
import id.pdam.sia.billing.domain.BillingBatchStatus;

import java.time.Instant;
import java.util.UUID;

public record BillingBatchResponse(
        UUID id,
        String batchNumber,
        String period,
        String areaCode,
        BillingBatchStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static BillingBatchResponse from(BillingBatch batch) {
        return new BillingBatchResponse(
                batch.getId(),
                batch.getBatchNumber(),
                batch.getPeriod(),
                batch.getAreaCode(),
                batch.getStatus(),
                batch.getCreatedAt(),
                batch.getUpdatedAt()
        );
    }
}
