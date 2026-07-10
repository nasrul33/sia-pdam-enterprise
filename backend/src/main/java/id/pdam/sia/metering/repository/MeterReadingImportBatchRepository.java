package id.pdam.sia.metering.repository;

import id.pdam.sia.metering.domain.MeterReadingImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MeterReadingImportBatchRepository extends JpaRepository<MeterReadingImportBatch, UUID> {
    Optional<MeterReadingImportBatch> findBySourceBatchReference(String sourceBatchReference);
}
