package id.pdam.sia.accounting.web;

import id.pdam.sia.accounting.domain.JournalLine;

import java.math.BigDecimal;
import java.util.UUID;

public record JournalLineResponse(
        UUID id,
        UUID accountId,
        BigDecimal debit,
        BigDecimal credit,
        String description
) {
    public static JournalLineResponse from(JournalLine line) {
        return new JournalLineResponse(
                line.getId(),
                line.getAccountId(),
                line.getDebit(),
                line.getCredit(),
                line.getDescription()
        );
    }
}
