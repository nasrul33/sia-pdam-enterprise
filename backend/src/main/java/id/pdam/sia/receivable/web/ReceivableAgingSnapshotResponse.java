package id.pdam.sia.receivable.web;

import id.pdam.sia.receivable.domain.ReceivableAgingSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReceivableAgingSnapshotResponse(
        UUID id,
        String period,
        BigDecimal currentAmount,
        BigDecimal bucket30Amount,
        BigDecimal bucket60Amount,
        BigDecimal bucket90Amount,
        BigDecimal bucketOver90Amount,
        BigDecimal totalOutstandingAmount,
        Instant generatedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static ReceivableAgingSnapshotResponse from(ReceivableAgingSnapshot snapshot) {
        return new ReceivableAgingSnapshotResponse(
                snapshot.getId(),
                snapshot.getPeriod(),
                snapshot.getCurrentAmount(),
                snapshot.getBucket30Amount(),
                snapshot.getBucket60Amount(),
                snapshot.getBucket90Amount(),
                snapshot.getBucketOver90Amount(),
                snapshot.getCurrentAmount()
                        .add(snapshot.getBucket30Amount())
                        .add(snapshot.getBucket60Amount())
                        .add(snapshot.getBucket90Amount())
                        .add(snapshot.getBucketOver90Amount()),
                snapshot.getGeneratedAt(),
                snapshot.getCreatedAt(),
                snapshot.getUpdatedAt()
        );
    }
}
