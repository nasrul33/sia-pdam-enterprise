package id.pdam.sia.shared.audit;

public record AuditTrailEntry(
        String actor,
        String module,
        String action,
        String recordId,
        String reason,
        String beforeValue,
        String afterValue,
        String correlationId,
        String ipAddress,
        String userAgent
) {
    public static AuditTrailEntry sensitiveAction(
            String actor,
            String module,
            String action,
            String recordId,
            String reason
    ) {
        return new AuditTrailEntry(actor, module, action, recordId, reason, null, null, null, null, null);
    }
}
