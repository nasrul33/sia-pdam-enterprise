package id.pdam.sia.accounting.web;

import id.pdam.sia.accounting.domain.JournalEntry;
import id.pdam.sia.accounting.domain.JournalStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JournalResponse(
        UUID id,
        String journalNumber,
        UUID accountingPeriodId,
        String description,
        JournalStatus status,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        boolean balanced,
        Instant postedAt,
        String postedBy,
        String sourceModule,
        UUID sourceRecordId,
        String sourceDocumentNumber,
        List<JournalLineResponse> lines,
        List<String> availableActions,
        Instant createdAt,
        Instant updatedAt
) {
    public static JournalResponse from(JournalEntry journal) {
        return new JournalResponse(
                journal.getId(),
                journal.getJournalNumber(),
                journal.getAccountingPeriodId(),
                journal.getDescription(),
                journal.getStatus(),
                journal.totalDebit(),
                journal.totalCredit(),
                journal.isBalanced(),
                journal.getPostedAt(),
                journal.getPostedBy(),
                journal.getSourceModule(),
                journal.getSourceRecordId(),
                journal.getSourceDocumentNumber(),
                journal.getLines().stream().map(JournalLineResponse::from).toList(),
                availableActions(journal),
                journal.getCreatedAt(),
                journal.getUpdatedAt()
        );
    }

    private static List<String> availableActions(JournalEntry journal) {
        return switch (journal.getStatus()) {
            case DRAFT -> List.of("POST", "VOID");
            case POSTED -> List.of("REVERSE");
            case REVERSED, VOID -> List.of();
        };
    }
}
