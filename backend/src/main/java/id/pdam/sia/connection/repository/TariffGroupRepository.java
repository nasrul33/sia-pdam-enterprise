package id.pdam.sia.connection.repository;

import id.pdam.sia.connection.domain.TariffGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TariffGroupRepository extends JpaRepository<TariffGroup, UUID> {
    Optional<TariffGroup> findByCode(String code);
}
