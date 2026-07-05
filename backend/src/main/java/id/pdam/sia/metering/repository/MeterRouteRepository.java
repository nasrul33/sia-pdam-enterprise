package id.pdam.sia.metering.repository;

import id.pdam.sia.metering.domain.MeterRoute;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MeterRouteRepository extends JpaRepository<MeterRoute, UUID> {
    Optional<MeterRoute> findByRouteCode(String routeCode);

    Page<MeterRoute> findByAreaCode(String areaCode, Pageable pageable);
}
