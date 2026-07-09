package id.pdam.sia.reporting.application;

import java.time.Instant;
import java.util.List;

public record PaymentReconciliationHandoffStalePacketReport(
        List<PaymentReconciliationHandoffWorkloadEntry> rows,
        String packetScopeHash,
        String filterSnapshot,
        long staleNoteCount,
        long ownerCount,
        long maxOverdueDays,
        Instant generatedAt
) {
}
