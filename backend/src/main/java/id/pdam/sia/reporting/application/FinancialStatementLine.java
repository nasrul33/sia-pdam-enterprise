package id.pdam.sia.reporting.application;

import id.pdam.sia.accounting.domain.AccountType;

import java.math.BigDecimal;
import java.util.UUID;

public record FinancialStatementLine(
        UUID accountId,
        String accountCode,
        String accountName,
        AccountType accountType,
        BigDecimal amount
) {
}
