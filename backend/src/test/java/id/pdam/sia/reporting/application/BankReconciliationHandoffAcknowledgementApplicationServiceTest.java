package id.pdam.sia.reporting.application;

import id.pdam.sia.reporting.domain.PaymentReconciliationHandoffAcknowledgement;
import id.pdam.sia.reporting.repository.PaymentReconciliationHandoffAcknowledgementRepository;
import id.pdam.sia.shared.audit.AuditTrailService;
import id.pdam.sia.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BankReconciliationHandoffAcknowledgementApplicationServiceTest {
    private final BankReconciliationHandoffWorkloadApplicationService workloadApplicationService =
            mock(BankReconciliationHandoffWorkloadApplicationService.class);
    private final PaymentReconciliationHandoffAcknowledgementRepository acknowledgementRepository =
            mock(PaymentReconciliationHandoffAcknowledgementRepository.class);
    private final AuditTrailService auditTrailService = mock(AuditTrailService.class);
    private final BankReconciliationHandoffAcknowledgementApplicationService service =
            new BankReconciliationHandoffAcknowledgementApplicationService(
                    workloadApplicationService,
                    acknowledgementRepository,
                    auditTrailService
            );

    @Test
    void acknowledgesCurrentStalePacketWithAuditTrail() {
        PaymentReconciliationHandoffWorkloadFilters filters =
                new PaymentReconciliationHandoffWorkloadFilters(null, " finance.ops ", false, null, null);
        when(workloadApplicationService.staleEvidencePacket(filters)).thenReturn(report("sha256:current", 3));
        when(acknowledgementRepository.findByPacketScopeHash("sha256:current")).thenReturn(Optional.empty());
        when(acknowledgementRepository.save(any(PaymentReconciliationHandoffAcknowledgement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentReconciliationHandoffAcknowledgement result = service.acknowledgeStalePacket(
                filters,
                new PaymentReconciliationHandoffAcknowledgementCommand(
                        " sha256:current ",
                        "Supervisor sudah review packet stale harian."
                ),
                "finance.supervisor"
        );

        assertThat(result.getPacketScopeHash()).isEqualTo("sha256:current");
        assertThat(result.getFilterSnapshot()).isEqualTo("handoffStatus=ALL|handoffOwner=finance.ops");
        assertThat(result.getStaleNoteCount()).isEqualTo(3);
        assertThat(result.getOwnerCount()).isEqualTo(2);
        assertThat(result.getMaxOverdueDays()).isEqualTo(9);
        assertThat(result.getAcknowledgedBy()).isEqualTo("finance.supervisor");
        assertThat(result.getAcknowledgedAt()).isNotNull();
        verify(auditTrailService).record(
                "finance.supervisor",
                "PAYMENT",
                "ACKNOWLEDGE_RECONCILIATION_STALE_HANDOFF_PACKET",
                result.getId().toString(),
                "Supervisor sudah review packet stale harian."
        );
    }

    @Test
    void duplicatePacketHashReturnsExistingAcknowledgementWithoutDuplicateAudit() {
        PaymentReconciliationHandoffWorkloadFilters filters =
                new PaymentReconciliationHandoffWorkloadFilters(null, null, false, null, null);
        PaymentReconciliationHandoffAcknowledgement existing = new PaymentReconciliationHandoffAcknowledgement(
                "sha256:current",
                "handoffStatus=ALL",
                2,
                1,
                5,
                "finance.supervisor",
                Instant.parse("2026-08-01T10:00:00Z"),
                "Reviewed earlier."
        );
        when(workloadApplicationService.staleEvidencePacket(filters)).thenReturn(report("sha256:current", 2));
        when(acknowledgementRepository.findByPacketScopeHash("sha256:current")).thenReturn(Optional.of(existing));

        PaymentReconciliationHandoffAcknowledgement result = service.acknowledgeStalePacket(
                filters,
                new PaymentReconciliationHandoffAcknowledgementCommand("sha256:current", "Retry submit."),
                "finance.supervisor"
        );

        assertThat(result).isSameAs(existing);
        verify(acknowledgementRepository, never()).save(any());
        verify(auditTrailService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsStaleScopeHashBeforePersistence() {
        PaymentReconciliationHandoffWorkloadFilters filters =
                new PaymentReconciliationHandoffWorkloadFilters(null, null, false, null, null);
        when(workloadApplicationService.staleEvidencePacket(filters)).thenReturn(report("sha256:new", 1));

        assertThatThrownBy(() -> service.acknowledgeStalePacket(
                filters,
                new PaymentReconciliationHandoffAcknowledgementCommand("sha256:old", "Review stale packet."),
                "finance.supervisor"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("scope has changed");

        verify(acknowledgementRepository, never()).save(any());
        verify(auditTrailService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsEmptyStalePacketBeforePersistence() {
        PaymentReconciliationHandoffWorkloadFilters filters =
                new PaymentReconciliationHandoffWorkloadFilters(null, null, false, null, null);
        when(workloadApplicationService.staleEvidencePacket(filters)).thenReturn(report("sha256:empty", 0));

        assertThatThrownBy(() -> service.acknowledgeStalePacket(
                filters,
                new PaymentReconciliationHandoffAcknowledgementCommand("sha256:empty", "Tidak ada stale."),
                "finance.supervisor"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No stale handoff packet");

        verify(acknowledgementRepository, never()).save(any());
        verify(auditTrailService, never()).record(any(), any(), any(), any(), any());
    }

    private static PaymentReconciliationHandoffStalePacketReport report(String hash, long staleNoteCount) {
        return new PaymentReconciliationHandoffStalePacketReport(
                List.of(),
                hash,
                "handoffStatus=ALL|handoffOwner=finance.ops",
                staleNoteCount,
                2,
                9,
                Instant.parse("2026-08-01T10:00:00Z")
        );
    }
}
