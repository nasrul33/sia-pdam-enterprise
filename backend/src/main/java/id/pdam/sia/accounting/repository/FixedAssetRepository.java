package id.pdam.sia.accounting.repository;

import id.pdam.sia.accounting.domain.FixedAsset;
import id.pdam.sia.accounting.domain.FixedAssetStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface FixedAssetRepository extends JpaRepository<FixedAsset, UUID> {
    Optional<FixedAsset> findByAssetCode(String assetCode);

    Page<FixedAsset> findByStatus(FixedAssetStatus status, Pageable pageable);

    @Query("""
            select count(asset)
            from FixedAsset asset
            where asset.acquisitionDate <= :periodEnd
              and asset.accumulatedDepreciation < (asset.acquisitionCost - asset.salvageValue)
              and (
                  asset.status = :activeStatus
                  or (
                      asset.status = :disposedStatus
                      and asset.disposedAt >= :periodEndExclusive
                  )
              )
              and not exists (
                  select depreciation.id
                  from FixedAssetDepreciation depreciation
                  where depreciation.assetId = asset.id
                    and depreciation.period = :period
              )
            """)
    long countMissingDepreciationForPeriod(
            @Param("period") String period,
            @Param("periodEnd") LocalDate periodEnd,
            @Param("periodEndExclusive") Instant periodEndExclusive,
            @Param("activeStatus") FixedAssetStatus activeStatus,
            @Param("disposedStatus") FixedAssetStatus disposedStatus
    );
}
