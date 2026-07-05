package id.pdam.sia.accounting.repository;

import id.pdam.sia.accounting.domain.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {
    Optional<JournalEntry> findByJournalNumber(String journalNumber);
}
