package id.pdam.sia.receivable.repository;

import id.pdam.sia.receivable.domain.InstallmentPlan;
import id.pdam.sia.receivable.domain.InstallmentPlanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InstallmentPlanRepository extends JpaRepository<InstallmentPlan, UUID> {
    boolean existsByInvoiceIdAndStatus(UUID invoiceId, InstallmentPlanStatus status);

    Optional<InstallmentPlan> findByPlanNumber(String planNumber);

    Page<InstallmentPlan> findByStatus(InstallmentPlanStatus status, Pageable pageable);
}
