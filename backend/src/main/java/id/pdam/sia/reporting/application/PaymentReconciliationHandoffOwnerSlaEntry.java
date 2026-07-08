package id.pdam.sia.reporting.application;

import id.pdam.sia.reporting.domain.PaymentReconciliationHandoffNote;
import id.pdam.sia.reporting.domain.PaymentReconciliationHandoffStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public record PaymentReconciliationHandoffOwnerSlaEntry(
        String handoffOwner,
        String ownerLabel,
        boolean unassigned,
        long totalNotes,
        long openNotes,
        long inProgressNotes,
        long clearedNotes,
        long overdueNotes,
        LocalDate nearestDueDate,
        long maxOverdueDays,
        Instant latestUpdatedAt,
        String escalationPriority,
        Instant generatedAt
) {
    public static PaymentReconciliationHandoffOwnerSlaEntry from(
            String ownerKey,
            List<PaymentReconciliationHandoffNote> notes,
            LocalDate generatedDate,
            Instant generatedAt
    ) {
        boolean unassigned = ownerKey.equals(BankReconciliationHandoffWorkloadApplicationService.UNASSIGNED_OWNER_KEY);
        String handoffOwner = unassigned ? null : ownerKey;
        long openNotes = countStatus(notes, PaymentReconciliationHandoffStatus.OPEN);
        long inProgressNotes = countStatus(notes, PaymentReconciliationHandoffStatus.IN_PROGRESS);
        long clearedNotes = countStatus(notes, PaymentReconciliationHandoffStatus.CLEARED);
        long overdueNotes = notes.stream()
                .filter(note -> note.getHandoffStatus() != PaymentReconciliationHandoffStatus.CLEARED)
                .filter(note -> BankReconciliationHandoffWorkloadApplicationService.overdueDays(note, generatedDate) > 0)
                .count();
        long maxOverdueDays = notes.stream()
                .mapToLong(note -> BankReconciliationHandoffWorkloadApplicationService.overdueDays(note, generatedDate))
                .max()
                .orElse(0);
        LocalDate nearestDueDate = notes.stream()
                .filter(note -> note.getHandoffStatus() != PaymentReconciliationHandoffStatus.CLEARED)
                .map(PaymentReconciliationHandoffNote::getHandoffDueDate)
                .filter(dueDate -> dueDate != null)
                .min(Comparator.naturalOrder())
                .orElse(null);
        Instant latestUpdatedAt = notes.stream()
                .map(PaymentReconciliationHandoffNote::getUpdatedAt)
                .filter(updatedAt -> updatedAt != null)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new PaymentReconciliationHandoffOwnerSlaEntry(
                handoffOwner,
                unassigned ? "UNASSIGNED" : ownerKey,
                unassigned,
                notes.size(),
                openNotes,
                inProgressNotes,
                clearedNotes,
                overdueNotes,
                nearestDueDate,
                maxOverdueDays,
                latestUpdatedAt,
                priority(maxOverdueDays, overdueNotes, openNotes + inProgressNotes),
                generatedAt
        );
    }

    private static long countStatus(List<PaymentReconciliationHandoffNote> notes, PaymentReconciliationHandoffStatus status) {
        return notes.stream()
                .filter(note -> note.getHandoffStatus() == status)
                .count();
    }

    private static String priority(long maxOverdueDays, long overdueNotes, long activeNotes) {
        if (maxOverdueDays >= 7) {
            return "CRITICAL";
        }
        if (overdueNotes > 0) {
            return "OVERDUE";
        }
        if (activeNotes > 0) {
            return "ACTIVE";
        }
        return "CLEARED";
    }
}
