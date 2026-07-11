package id.pdam.sia.accounting.repository;

import id.pdam.sia.accounting.domain.FixedAssetDepreciation;
import id.pdam.sia.accounting.domain.FixedAssetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FixedAssetDepreciationRepository extends JpaRepository<FixedAssetDepreciation, UUID> {
    boolean existsByAssetIdAndPeriod(UUID assetId, String period);

    List<FixedAssetDepreciation> findByAssetIdOrderByPeriodDesc(UUID assetId);

    @Query("""
            select count(depreciation)
            from FixedAssetDepreciation depreciation
            where depreciation.period = :period
              and depreciation.assetId in (
                  select asset.id
                  from FixedAsset asset
                  where asset.status = :status
                    and asset.acquisitionDate <= :periodEnd
              )
            """)
    long countForActiveAssetsByPeriod(
            @Param("period") String period,
            @Param("status") FixedAssetStatus status,
            @Param("periodEnd") LocalDate periodEnd
    );
}
