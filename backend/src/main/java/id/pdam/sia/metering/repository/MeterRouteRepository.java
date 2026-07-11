package id.pdam.sia.metering.repository;

import id.pdam.sia.metering.domain.MeterRoute;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MeterRouteRepository extends JpaRepository<MeterRoute, UUID> {
    Optional<MeterRoute> findByRouteCode(String routeCode);

    Page<MeterRoute> findByAreaCode(String areaCode, Pageable pageable);

    @Query("""
            select route from MeterRoute route
            where (:areaCode is null or route.areaCode = :areaCode)
              and (:search is null
                   or lower(route.routeCode) like lower(concat('%', :search, '%'))
                   or lower(route.name) like lower(concat('%', :search, '%'))
                   or lower(route.areaCode) like lower(concat('%', :search, '%')))
            """)
    Page<MeterRoute> search(
            @Param("areaCode") String areaCode,
            @Param("search") String search,
            Pageable pageable
    );
}
