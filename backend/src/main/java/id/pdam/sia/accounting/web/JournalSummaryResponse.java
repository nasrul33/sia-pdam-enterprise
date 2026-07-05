package id.pdam.sia.accounting.web;

import id.pdam.sia.accounting.domain.JournalEntry;
import id.pdam.sia.accounting.domain.JournalStatus;

import java.time.Instant;
import java.util.UUID;

public record JournalSummaryResponse(
        UUID id,
        String journalNumber,
        UUID accountingPeriodId,
        String description,
        JournalStatus status,
        Instant postedAt,
        String postedBy,
        String sourceModule,
        UUID sourceRecordId,
        String sourceDocumentNumber,
        Instant createdAt,
        Instant updatedAt
) {
    public static JournalSummaryResponse from(JournalEntry journal) {
        return new JournalSummaryResponse(
                journal.getId(),
                journal.getJournalNumber(),
                journal.getAccountingPeriodId(),
                journal.getDescription(),
                journal.getStatus(),
                journal.getPostedAt(),
                journal.getPostedBy(),
                journal.getSourceModule(),
                journal.getSourceRecordId(),
                journal.getSourceDocumentNumber(),
                journal.getCreatedAt(),
                journal.getUpdatedAt()
        );
    }
}
