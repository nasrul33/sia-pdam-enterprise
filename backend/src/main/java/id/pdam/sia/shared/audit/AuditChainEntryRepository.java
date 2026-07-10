package id.pdam.sia.shared.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditChainEntryRepository extends JpaRepository<AuditChainEntry, UUID> {
    boolean existsByAuditLogId(UUID auditLogId);

    Optional<AuditChainEntry> findTopByOrderBySequenceNoDesc();

    List<AuditChainEntry> findAllByOrderBySequenceNoAsc();
}
