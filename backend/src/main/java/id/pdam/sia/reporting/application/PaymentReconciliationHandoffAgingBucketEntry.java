package id.pdam.sia.reporting.application;

import id.pdam.sia.reporting.domain.PaymentReconciliationHandoffNote;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public record PaymentReconciliationHandoffAgingBucketEntry(
        String handoffOwner,
        String ownerLabel,
        boolean unassigned,
        long activeNotes,
        long dueTodayNotes,
        long overdue1To3Notes,
        long overdue4To7Notes,
        long overdueOver7Notes,
        long futureDueNotes,
        long noDueDateNotes,
        long staleNotes,
        LocalDate nearestDueDate,
        long maxOverdueDays,
        Instant latestUpdatedAt,
        Instant generatedAt
) {
    public static PaymentReconciliationHandoffAgingBucketEntry from(
            String ownerKey,
            List<PaymentReconciliationHandoffNote> notes,
            LocalDate generatedDate,
            Instant generatedAt
    ) {
        boolean unassigned = ownerKey.equals(BankReconciliationHandoffWorkloadApplicationService.UNASSIGNED_OWNER_KEY);
        long dueTodayNotes = notes.stream()
                .filter(note -> generatedDate.equals(note.getHandoffDueDate()))
                .count();
        long overdue1To3Notes = countOverdueRange(notes, generatedDate, 1, 3);
        long overdue4To7Notes = countOverdueRange(notes, generatedDate, 4, 7);
        long overdueOver7Notes = notes.stream()
                .filter(note -> BankReconciliationHandoffWorkloadApplicationService.overdueDays(note, generatedDate) > 7)
                .count();
        long futureDueNotes = notes.stream()
                .map(PaymentReconciliationHandoffNote::getHandoffDueDate)
                .filter(dueDate -> dueDate != null && dueDate.isAfter(generatedDate))
                .count();
        long noDueDateNotes = notes.stream()
                .filter(note -> note.getHandoffDueDate() == null)
                .count();
        long staleNotes = overdue1To3Notes + overdue4To7Notes + overdueOver7Notes;
        long maxOverdueDays = notes.stream()
                .mapToLong(note -> BankReconciliationHandoffWorkloadApplicationService.overdueDays(note, generatedDate))
                .max()
                .orElse(0);
        LocalDate nearestDueDate = notes.stream()
                .map(PaymentReconciliationHandoffNote::getHandoffDueDate)
                .filter(dueDate -> dueDate != null)
                .min(Comparator.naturalOrder())
                .orElse(null);
        Instant latestUpdatedAt = notes.stream()
                .map(PaymentReconciliationHandoffNote::getUpdatedAt)
                .filter(updatedAt -> updatedAt != null)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new PaymentReconciliationHandoffAgingBucketEntry(
                unassigned ? null : ownerKey,
                unassigned ? "UNASSIGNED" : ownerKey,
                unassigned,
                notes.size(),
                dueTodayNotes,
                overdue1To3Notes,
                overdue4To7Notes,
                overdueOver7Notes,
                futureDueNotes,
                noDueDateNotes,
                staleNotes,
                nearestDueDate,
                maxOverdueDays,
                latestUpdatedAt,
                generatedAt
        );
    }

    private static long countOverdueRange(
            List<PaymentReconciliationHandoffNote> notes,
            LocalDate generatedDate,
            long fromDays,
            long toDays
    ) {
        return notes.stream()
                .mapToLong(note -> BankReconciliationHandoffWorkloadApplicationService.overdueDays(note, generatedDate))
                .filter(days -> days >= fromDays && days <= toDays)
                .count();
    }
}
