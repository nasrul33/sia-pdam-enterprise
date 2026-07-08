package id.pdam.sia.reporting.application;

import java.time.Instant;
import java.util.List;

public record PaymentReconciliationHandoffOwnerSlaReport(
        List<PaymentReconciliationHandoffOwnerSlaEntry> owners,
        long totalNotes,
        long openNotes,
        long inProgressNotes,
        long clearedNotes,
        long overdueNotes,
        boolean truncated,
        Instant generatedAt
) {}
