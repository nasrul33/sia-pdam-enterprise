package id.pdam.sia.accounting.repository;

import id.pdam.sia.accounting.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByCode(String code);
}
