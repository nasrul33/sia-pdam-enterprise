package id.pdam.sia.billing.repository;

import id.pdam.sia.billing.domain.BillingBatch;
import id.pdam.sia.billing.domain.BillingBatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface BillingBatchRepository extends JpaRepository<BillingBatch, UUID> {
    Optional<BillingBatch> findByPeriodAndAreaCode(String period, String areaCode);

    Optional<BillingBatch> findByIdempotencyKey(String idempotencyKey);

    Page<BillingBatch> findByPeriod(String period, Pageable pageable);

    Page<BillingBatch> findByStatus(BillingBatchStatus status, Pageable pageable);

    Page<BillingBatch> findByPeriodAndStatus(String period, BillingBatchStatus status, Pageable pageable);

    long countByPeriodAndStatusNotIn(String period, Collection<BillingBatchStatus> statuses);
}
