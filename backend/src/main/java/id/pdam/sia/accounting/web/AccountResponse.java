package id.pdam.sia.accounting.web;

import id.pdam.sia.accounting.domain.Account;
import id.pdam.sia.accounting.domain.AccountType;
import id.pdam.sia.accounting.domain.NormalBalance;

import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String code,
        String name,
        AccountType type,
        NormalBalance normalBalance,
        Instant createdAt,
        Instant updatedAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getCode(),
                account.getName(),
                account.getType(),
                account.getNormalBalance(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
