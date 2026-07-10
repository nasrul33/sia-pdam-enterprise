package id.pdam.sia.shared.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditTrailService {
    private final AuditLogRepository auditLogRepository;
    private final AuditChainApplicationService auditChainApplicationService;

    public AuditTrailService(
            AuditLogRepository auditLogRepository,
            AuditChainApplicationService auditChainApplicationService
    ) {
        this.auditLogRepository = auditLogRepository;
        this.auditChainApplicationService = auditChainApplicationService;
    }

    @Transactional
    public AuditLog record(String actor, String module, String action, String recordId, String reason) {
        return record(AuditTrailEntry.sensitiveAction(actor, module, action, recordId, reason));
    }

    @Transactional
    public AuditLog record(AuditTrailEntry entry) {
        AuditLog auditLog = auditLogRepository.save(AuditLog.from(entry));
        auditChainApplicationService.append(auditLog);
        return auditLog;
    }
}
