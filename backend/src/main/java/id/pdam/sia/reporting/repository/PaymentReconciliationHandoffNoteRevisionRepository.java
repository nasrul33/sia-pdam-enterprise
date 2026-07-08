package id.pdam.sia.reporting.repository;

import id.pdam.sia.reporting.domain.PaymentReconciliationHandoffNoteRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PaymentReconciliationHandoffNoteRevisionRepository
        extends JpaRepository<PaymentReconciliationHandoffNoteRevision, UUID> {
    long countByNoteId(UUID noteId);

    List<PaymentReconciliationHandoffNoteRevision> findByNoteIdOrderByRevisionNumberAsc(UUID noteId);

    List<PaymentReconciliationHandoffNoteRevision> findByNoteIdInOrderByNoteIdAscRevisionNumberAsc(
            Collection<UUID> noteIds
    );
}
