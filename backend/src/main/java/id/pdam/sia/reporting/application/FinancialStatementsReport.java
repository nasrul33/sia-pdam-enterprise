package id.pdam.sia.reporting.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record FinancialStatementsReport(
        LocalDate fromDate,
        LocalDate toDate,
        List<FinancialStatementLine> assets,
        List<FinancialStatementLine> liabilities,
        List<FinancialStatementLine> equity,
        List<FinancialStatementLine> revenue,
        List<FinancialStatementLine> expenses,
        BigDecimal totalAssets,
        BigDecimal totalLiabilities,
        BigDecimal totalEquity,
        BigDecimal totalRevenue,
        BigDecimal totalExpenses,
        BigDecimal netIncome,
        boolean trialBalanceBalanced,
        Instant generatedAt
) {
    public FinancialStatementsReport {
        assets = List.copyOf(assets);
        liabilities = List.copyOf(liabilities);
        equity = List.copyOf(equity);
        revenue = List.copyOf(revenue);
        expenses = List.copyOf(expenses);
    }
}
