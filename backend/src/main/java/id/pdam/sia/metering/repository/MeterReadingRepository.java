package id.pdam.sia.metering.repository;

import id.pdam.sia.metering.domain.MeterReading;
import id.pdam.sia.metering.domain.MeterReadingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

    @Query("""
            select reading
            from MeterReading reading
            where reading.period = :period
              and reading.status = id.pdam.sia.metering.domain.MeterReadingStatus.VERIFIED
              and reading.routeId in (
                  select route.id
                  from MeterRoute route
                  where route.areaCode = :areaCode
              )
            order by reading.connectionId
            """)
    List<MeterReading> findVerifiedByAreaCodeAndPeriod(
            @Param("areaCode") String areaCode,
            @Param("period") String period
    );
}
