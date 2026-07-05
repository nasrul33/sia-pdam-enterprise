package id.pdam.sia.billing.repository;

import id.pdam.sia.billing.domain.TariffBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TariffBlockRepository extends JpaRepository<TariffBlock, UUID> {
    boolean existsByTariffVersionIdAndBlockOrder(UUID tariffVersionId, int blockOrder);

    List<TariffBlock> findByTariffVersionIdOrderByBlockOrderAsc(UUID tariffVersionId);
}
