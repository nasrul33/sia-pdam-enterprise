package id.pdam.sia.receivable.repository;

import id.pdam.sia.receivable.domain.InstallmentItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InstallmentItemRepository extends JpaRepository<InstallmentItem, UUID> {
    List<InstallmentItem> findByPlanIdOrderByInstallmentNumberAsc(UUID planId);
}
