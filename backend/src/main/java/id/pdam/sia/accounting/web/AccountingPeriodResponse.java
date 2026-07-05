package id.pdam.sia.accounting.web;

import id.pdam.sia.accounting.domain.AccountingPeriod;
import id.pdam.sia.accounting.domain.PeriodStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AccountingPeriodResponse(
        UUID id,
        String period,
        PeriodStatus status,
        boolean allowsPosting,
        List<String> availableActions,
        Instant createdAt,
        Instant updatedAt
) {
    public static AccountingPeriodResponse from(AccountingPeriod period) {
        return new AccountingPeriodResponse(
                period.getId(),
                period.getPeriod(),
                period.getStatus(),
                period.allowsPosting(),
                availableActions(period),
                period.getCreatedAt(),
                period.getUpdatedAt()
        );
    }

    private static List<String> availableActions(AccountingPeriod period) {
        return switch (period.getStatus()) {
            case OPEN -> List.of("START_CLOSING_REVIEW");
            case CLOSING_REVIEW -> List.of("LOCK");
            case REOPENED -> List.of("START_CLOSING_REVIEW");
            case LOCKED -> List.of();
        };
    }
}
