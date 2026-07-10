package id.pdam.sia.shared.audit;

import id.pdam.sia.shared.security.Permissions;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-chain")
public class AuditChainController {
    private final AuditChainApplicationService auditChainApplicationService;

    public AuditChainController(AuditChainApplicationService auditChainApplicationService) {
        this.auditChainApplicationService = auditChainApplicationService;
    }

    @GetMapping("/verify")
    @PreAuthorize(Permissions.AUDIT_CHAIN_VERIFY)
    public AuditChainApplicationService.AuditChainVerificationReport verify() {
        return auditChainApplicationService.verify();
    }
}
