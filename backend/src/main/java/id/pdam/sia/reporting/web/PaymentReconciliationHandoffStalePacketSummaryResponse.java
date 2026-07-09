package id.pdam.sia.reporting.web;

import id.pdam.sia.reporting.application.PaymentReconciliationHandoffStalePacketReport;

import java.time.Instant;

public record PaymentReconciliationHandoffStalePacketSummaryResponse(
        String packetScopeHash,
        String filterSnapshot,
        long staleNoteCount,
        long ownerCount,
        long maxOverdueDays,
        Instant generatedAt
) {
    public static PaymentReconciliationHandoffStalePacketSummaryResponse from(
            PaymentReconciliationHandoffStalePacketReport report
    ) {
        return new PaymentReconciliationHandoffStalePacketSummaryResponse(
                report.packetScopeHash(),
                report.filterSnapshot(),
                report.staleNoteCount(),
                report.ownerCount(),
                report.maxOverdueDays(),
                report.generatedAt()
        );
    }
}
