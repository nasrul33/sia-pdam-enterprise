package id.pdam.sia.receivable.repository;

import id.pdam.sia.receivable.domain.ReceivableAgingSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReceivableAgingSnapshotRepository extends JpaRepository<ReceivableAgingSnapshot, UUID> {
    Optional<ReceivableAgingSnapshot> findByPeriod(String period);
}
