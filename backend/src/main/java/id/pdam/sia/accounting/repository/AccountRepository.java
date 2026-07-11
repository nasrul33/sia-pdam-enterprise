package id.pdam.sia.accounting.repository;

import id.pdam.sia.accounting.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByCode(String code);

    @Query("""
            select account from Account account
            where lower(account.code) like lower(concat('%', :search, '%'))
               or lower(account.name) like lower(concat('%', :search, '%'))
            """)
    Page<Account> search(@Param("search") String search, Pageable pageable);
}
