package id.pdam.sia.shared.audit;

import id.pdam.sia.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditTrailServiceTest {
    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final AuditChainApplicationService auditChainApplicationService = mock(AuditChainApplicationService.class);
    private final AuditTrailService auditTrailService = new AuditTrailService(auditLogRepository, auditChainApplicationService);

    @Test
    void persistsSensitiveActionAuditLog() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuditLog auditLog = auditTrailService.record(
                "finance.supervisor",
                "ACCOUNTING",
                "POST_JOURNAL",
                "JV-2026-07-0001",
                "period closing"
        );

        assertThat(auditLog.getActor()).isEqualTo("finance.supervisor");
        assertThat(auditLog.getModule()).isEqualTo("ACCOUNTING");
        assertThat(auditLog.getAction()).isEqualTo("POST_JOURNAL");
        verify(auditLogRepository).save(any(AuditLog.class));
        verify(auditChainApplicationService).append(any(AuditLog.class));
    }

    @Test
    void rejectsAuditLogWithoutActor() {
        assertThatThrownBy(() -> auditTrailService.record(
                " ",
                "ACCOUNTING",
                "POST_JOURNAL",
                "JV-2026-07-0001",
                "period closing"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Audit actor is required");
    }
}
