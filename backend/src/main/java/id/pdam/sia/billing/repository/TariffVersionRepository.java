package id.pdam.sia.billing.repository;

import id.pdam.sia.billing.domain.TariffVersion;
import id.pdam.sia.billing.domain.TariffVersionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface TariffVersionRepository extends JpaRepository<TariffVersion, UUID> {
    boolean existsByTariffGroupIdAndEffectiveDate(UUID tariffGroupId, LocalDate effectiveDate);

    Optional<TariffVersion> findFirstByTariffGroupIdAndStatusAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
            UUID tariffGroupId,
            TariffVersionStatus status,
            LocalDate effectiveDate
    );

    Page<TariffVersion> findByTariffGroupId(UUID tariffGroupId, Pageable pageable);

    Page<TariffVersion> findByStatus(TariffVersionStatus status, Pageable pageable);

    Page<TariffVersion> findByTariffGroupIdAndStatus(UUID tariffGroupId, TariffVersionStatus status, Pageable pageable);
}
