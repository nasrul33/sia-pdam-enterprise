package id.pdam.sia.accounting.repository;

import id.pdam.sia.accounting.domain.FixedAsset;
import id.pdam.sia.accounting.domain.FixedAssetStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FixedAssetRepository extends JpaRepository<FixedAsset, UUID> {
    Optional<FixedAsset> findByAssetCode(String assetCode);

    Page<FixedAsset> findByStatus(FixedAssetStatus status, Pageable pageable);
}
