package id.pdam.sia.metering.repository;

import id.pdam.sia.metering.domain.MeterReading;
import id.pdam.sia.metering.domain.MeterReadingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MeterReadingRepository extends JpaRepository<MeterReading, UUID> {
    boolean existsByConnectionIdAndPeriod(UUID connectionId, String period);

    Optional<MeterReading> findByConnectionIdAndPeriod(UUID connectionId, String period);

    Page<MeterReading> findByRouteId(UUID routeId, Pageable pageable);

    Page<MeterReading> findByStatus(MeterReadingStatus status, Pageable pageable);

    Page<MeterReading> findByPeriod(String period, Pageable pageable);

    Page<MeterReading> findByPeriodAndStatus(String period, MeterReadingStatus status, Pageable pageable);

    Page<MeterReading> findByRouteIdAndPeriod(UUID routeId, String period, Pageable pageable);

    Page<MeterReading> findByRouteIdAndStatus(UUID routeId, MeterReadingStatus status, Pageable pageable);

    Page<MeterReading> findByRouteIdAndPeriodAndStatus(
            UUID routeId,
            String period,
            MeterReadingStatus status,
            Pageable pageable
    );
}
