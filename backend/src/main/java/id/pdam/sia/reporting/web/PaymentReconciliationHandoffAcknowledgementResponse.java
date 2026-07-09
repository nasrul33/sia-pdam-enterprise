package id.pdam.sia.reporting.web;

import id.pdam.sia.reporting.domain.PaymentReconciliationHandoffAcknowledgement;

import java.time.Instant;
import java.util.UUID;

public record PaymentReconciliationHandoffAcknowledgementResponse(
        UUID id,
        String packetScopeHash,
        String filterSnapshot,
        long staleNoteCount,
        long ownerCount,
        long maxOverdueDays,
        String acknowledgedBy,
        Instant acknowledgedAt,
        String reason,
        Instant createdAt,
        Instant updatedAt
) {
    public static PaymentReconciliationHandoffAcknowledgementResponse from(
            PaymentReconciliationHandoffAcknowledgement acknowledgement
    ) {
        return new PaymentReconciliationHandoffAcknowledgementResponse(
                acknowledgement.getId(),
                acknowledgement.getPacketScopeHash(),
                acknowledgement.getFilterSnapshot(),
                acknowledgement.getStaleNoteCount(),
                acknowledgement.getOwnerCount(),
                acknowledgement.getMaxOverdueDays(),
                acknowledgement.getAcknowledgedBy(),
                acknowledgement.getAcknowledgedAt(),
                acknowledgement.getReason(),
                acknowledgement.getCreatedAt(),
                acknowledgement.getUpdatedAt()
        );
    }
}
