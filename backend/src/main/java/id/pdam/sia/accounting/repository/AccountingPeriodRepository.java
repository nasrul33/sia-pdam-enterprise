package id.pdam.sia.accounting.repository;

import id.pdam.sia.accounting.domain.AccountingPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriod, UUID> {
    Optional<AccountingPeriod> findByPeriod(String period);
}
