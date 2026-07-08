package id.pdam.sia.shared.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    Optional<AuditLog> findFirstByModuleAndActionAndRecordIdOrderByCreatedAtDesc(
            String module,
            String action,
            String recordId
    );
}
