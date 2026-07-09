package id.pdam.sia.reporting.application;

import id.pdam.sia.reporting.domain.PaymentReconciliationHandoffAcknowledgement;
import id.pdam.sia.reporting.repository.PaymentReconciliationHandoffAcknowledgementRepository;
import id.pdam.sia.shared.audit.AuditTrailService;
import id.pdam.sia.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class BankReconciliationHandoffAcknowledgementApplicationService {
    private static final String PAYMENT_MODULE = "PAYMENT";
    private static final String ACKNOWLEDGE_STALE_HANDOFF_PACKET_ACTION =
            "ACKNOWLEDGE_RECONCILIATION_STALE_HANDOFF_PACKET";

    private final BankReconciliationHandoffWorkloadApplicationService workloadApplicationService;
    private final PaymentReconciliationHandoffAcknowledgementRepository acknowledgementRepository;
    private final AuditTrailService auditTrailService;

    public BankReconciliationHandoffAcknowledgementApplicationService(
            BankReconciliationHandoffWorkloadApplicationService workloadApplicationService,
            PaymentReconciliationHandoffAcknowledgementRepository acknowledgementRepository,
            AuditTrailService auditTrailService
    ) {
        this.workloadApplicationService = workloadApplicationService;
        this.acknowledgementRepository = acknowledgementRepository;
        this.auditTrailService = auditTrailService;
    }

    @Transactional(readOnly = true)
    public PaymentReconciliationHandoffStalePacketReport stalePacketSummary(
            PaymentReconciliationHandoffWorkloadFilters filters
    ) {
        return workloadApplicationService.staleEvidencePacket(filters);
    }

    @Transactional
    public PaymentReconciliationHandoffAcknowledgement acknowledgeStalePacket(
            PaymentReconciliationHandoffWorkloadFilters filters,
            PaymentReconciliationHandoffAcknowledgementCommand command,
            String actor
    ) {
        PaymentReconciliationHandoffAcknowledgementCommand safeCommand = requireCommand(command);
        String packetScopeHash = requireNormalize(
                safeCommand.packetScopeHash(),
                "PAYMENT_RECONCILIATION_HANDOFF_ACK_HASH_REQUIRED",
                "Stale handoff packet scope hash is required."
        );
        String reason = requireNormalize(
                safeCommand.reason(),
                "PAYMENT_RECONCILIATION_HANDOFF_ACK_REASON_REQUIRED",
                "Stale handoff acknowledgement reason is required."
        );
        String normalizedActor = requireNormalize(
                actor,
                "PAYMENT_RECONCILIATION_HANDOFF_ACK_ACTOR_REQUIRED",
                "Stale handoff acknowledgement actor is required."
        );

        PaymentReconciliationHandoffStalePacketReport report = workloadApplicationService.staleEvidencePacket(filters);
        if (report.staleNoteCount() <= 0) {
            throw new BusinessException(
                    "PAYMENT_RECONCILIATION_HANDOFF_ACK_EMPTY_PACKET",
                    "No stale handoff packet is available for acknowledgement."
            );
        }
        if (!packetScopeHash.equals(report.packetScopeHash())) {
            throw new BusinessException(
                    "PAYMENT_RECONCILIATION_HANDOFF_ACK_SCOPE_STALE",
                    "Stale handoff packet scope has changed. Refresh the packet before acknowledgement."
            );
        }

        return acknowledgementRepository.findByPacketScopeHash(packetScopeHash)
                .orElseGet(() -> createAcknowledgement(report, packetScopeHash, normalizedActor, reason));
    }

    private PaymentReconciliationHandoffAcknowledgement createAcknowledgement(
            PaymentReconciliationHandoffStalePacketReport report,
            String packetScopeHash,
            String actor,
            String reason
    ) {
        PaymentReconciliationHandoffAcknowledgement acknowledgement = acknowledgementRepository.save(
                new PaymentReconciliationHandoffAcknowledgement(
                        packetScopeHash,
                        report.filterSnapshot(),
                        report.staleNoteCount(),
                        report.ownerCount(),
                        report.maxOverdueDays(),
                        actor,
                        Instant.now(),
                        reason
                )
        );
        auditTrailService.record(
                actor,
                PAYMENT_MODULE,
                ACKNOWLEDGE_STALE_HANDOFF_PACKET_ACTION,
                acknowledgement.getId().toString(),
                reason
        );
        return acknowledgement;
    }

    private static PaymentReconciliationHandoffAcknowledgementCommand requireCommand(
            PaymentReconciliationHandoffAcknowledgementCommand command
    ) {
        if (command == null) {
            throw new BusinessException(
                    "PAYMENT_RECONCILIATION_HANDOFF_ACK_COMMAND_REQUIRED",
                    "Stale handoff acknowledgement command is required."
            );
        }
        return command;
    }

    private static String requireNormalize(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }
}
