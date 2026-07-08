package id.pdam.sia.reporting.repository;

import id.pdam.sia.reporting.domain.PaymentReconciliationHandoffNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentReconciliationHandoffNoteRepository extends JpaRepository<PaymentReconciliationHandoffNote, UUID>,
        JpaSpecificationExecutor<PaymentReconciliationHandoffNote> {
    List<PaymentReconciliationHandoffNote> findBySessionIdOrderByUpdatedAtDescCreatedAtDesc(UUID sessionId);

    List<PaymentReconciliationHandoffNote> findBySessionIdInOrderBySessionIdAscUpdatedAtDesc(Collection<UUID> sessionIds);

    Optional<PaymentReconciliationHandoffNote> findBySessionIdAndId(UUID sessionId, UUID id);
}
