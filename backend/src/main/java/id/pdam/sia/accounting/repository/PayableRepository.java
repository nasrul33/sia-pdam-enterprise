package id.pdam.sia.accounting.repository;

import id.pdam.sia.accounting.domain.Payable;
import id.pdam.sia.accounting.domain.PayableStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PayableRepository extends JpaRepository<Payable, UUID> {
    Optional<Payable> findByPayableNumber(String payableNumber);

    Page<Payable> findByStatus(PayableStatus status, Pageable pageable);

    Page<Payable> findByPeriod(String period, Pageable pageable);

    Page<Payable> findByPeriodAndStatus(String period, PayableStatus status, Pageable pageable);
}
