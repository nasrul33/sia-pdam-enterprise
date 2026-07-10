package id.pdam.sia.payment.repository;

import id.pdam.sia.payment.domain.BankMutation;
import id.pdam.sia.payment.domain.BankMutationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankMutationRepository extends JpaRepository<BankMutation, UUID> {
    boolean existsByExternalReference(String externalReference);

    Optional<BankMutation> findByExternalReference(String externalReference);

    Page<BankMutation> findByStatus(BankMutationStatus status, Pageable pageable);

    List<BankMutation> findByStatusAndTransactedAtGreaterThanEqualAndTransactedAtLessThanOrderByTransactedAtAsc(
            BankMutationStatus status,
            Instant from,
            Instant to
    );
}
