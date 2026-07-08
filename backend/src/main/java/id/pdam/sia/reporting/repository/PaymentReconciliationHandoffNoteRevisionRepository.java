package id.pdam.sia.reporting.repository;

import id.pdam.sia.reporting.domain.PaymentReconciliationHandoffNoteRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query("""
            select revision.noteId as noteId, count(revision.id) as revisionCount
            from PaymentReconciliationHandoffNoteRevision revision
            where revision.noteId in :noteIds
            group by revision.noteId
            """)
    List<PaymentReconciliationHandoffNoteRevisionCount> countRevisionsByNoteIdIn(@Param("noteIds") Collection<UUID> noteIds);
}
