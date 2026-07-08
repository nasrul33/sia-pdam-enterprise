package id.pdam.sia.reporting.application;

import java.time.Instant;
import java.util.List;

public record PaymentReconciliationHandoffAgingBucketReport(
        List<PaymentReconciliationHandoffAgingBucketEntry> owners,
        long activeNotes,
        long dueTodayNotes,
        long overdue1To3Notes,
        long overdue4To7Notes,
        long overdueOver7Notes,
        long futureDueNotes,
        long noDueDateNotes,
        long staleNotes,
        boolean truncated,
        Instant generatedAt
) {}
