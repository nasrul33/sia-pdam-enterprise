package id.pdam.sia.metering.repository;

import id.pdam.sia.metering.domain.MeterReadingImportItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MeterReadingImportItemRepository extends JpaRepository<MeterReadingImportItem, UUID> {
    List<MeterReadingImportItem> findByBatchIdOrderByRowNumberAsc(UUID batchId);
}
