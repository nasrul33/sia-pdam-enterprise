package id.pdam.sia.reporting.application;

import id.pdam.sia.accounting.domain.AccountType;
import id.pdam.sia.accounting.domain.NormalBalance;

import java.math.BigDecimal;
import java.util.UUID;

public record TrialBalanceLine(
        UUID accountId,
        String accountCode,
        String accountName,
        AccountType accountType,
        NormalBalance normalBalance,
        BigDecimal debitTotal,
        BigDecimal creditTotal,
        BigDecimal debitBalance,
        BigDecimal creditBalance
) {
}
