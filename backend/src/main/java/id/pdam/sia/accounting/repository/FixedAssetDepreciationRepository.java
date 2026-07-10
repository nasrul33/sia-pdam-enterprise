package id.pdam.sia.accounting.repository;

import id.pdam.sia.accounting.domain.FixedAssetDepreciation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FixedAssetDepreciationRepository extends JpaRepository<FixedAssetDepreciation, UUID> {
    boolean existsByAssetIdAndPeriod(UUID assetId, String period);

    List<FixedAssetDepreciation> findByAssetIdOrderByPeriodDesc(UUID assetId);
}
