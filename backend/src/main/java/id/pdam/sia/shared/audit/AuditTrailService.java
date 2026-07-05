package id.pdam.sia.shared.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditTrailService {
    private final AuditLogRepository auditLogRepository;

    public AuditTrailService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public AuditLog record(String actor, String module, String action, String recordId, String reason) {
        return record(AuditTrailEntry.sensitiveAction(actor, module, action, recordId, reason));
    }

    @Transactional
    public AuditLog record(AuditTrailEntry entry) {
        return auditLogRepository.save(AuditLog.from(entry));
    }
}
