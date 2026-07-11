package id.pdam.sia.accounting.repository;

import id.pdam.sia.accounting.domain.AccountingPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriod, UUID> {
    Optional<AccountingPeriod> findByPeriod(String period);

    Page<AccountingPeriod> findByPeriodContainingIgnoreCase(String search, Pageable pageable);
}
