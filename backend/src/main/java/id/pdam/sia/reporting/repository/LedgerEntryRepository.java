package id.pdam.sia.reporting.repository;

import id.pdam.sia.reporting.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    List<LedgerEntry> findByPostingDateBetween(LocalDate fromDate, LocalDate toDate);
}
