package id.pdam.sia.accounting.repository;

import id.pdam.sia.accounting.domain.JournalEntry;
import id.pdam.sia.accounting.domain.JournalStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {
    Optional<JournalEntry> findByJournalNumber(String journalNumber);

    boolean existsBySourceModuleAndSourceRecordId(String sourceModule, UUID sourceRecordId);

    Page<JournalEntry> findByStatus(JournalStatus status, Pageable pageable);

    Page<JournalEntry> findByAccountingPeriodId(UUID accountingPeriodId, Pageable pageable);

    Page<JournalEntry> findByAccountingPeriodIdAndStatus(UUID accountingPeriodId, JournalStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "lines")
    Optional<JournalEntry> findWithLinesById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "lines")
    @Query("select journal from JournalEntry journal where journal.id = :id")
    Optional<JournalEntry> findForPosting(@Param("id") UUID id);
}
