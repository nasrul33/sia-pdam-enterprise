package id.pdam.sia.reporting.application;

import id.pdam.sia.accounting.domain.AccountType;
import id.pdam.sia.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
public class FinancialStatementsApplicationService {
    private final PostedLedgerReportApplicationService postedLedgerReportApplicationService;

    public FinancialStatementsApplicationService(PostedLedgerReportApplicationService postedLedgerReportApplicationService) {
        this.postedLedgerReportApplicationService = postedLedgerReportApplicationService;
    }

    @Transactional(readOnly = true)
    public FinancialStatementsReport statements(LocalDate fromDate, LocalDate toDate) {
        TrialBalanceReport trialBalance = postedLedgerReportApplicationService.trialBalance(fromDate, toDate);
        List<FinancialStatementLine> assets = lines(trialBalance, AccountType.ASSET);
        List<FinancialStatementLine> liabilities = lines(trialBalance, AccountType.LIABILITY);
        List<FinancialStatementLine> equity = lines(trialBalance, AccountType.EQUITY);
        List<FinancialStatementLine> revenue = lines(trialBalance, AccountType.REVENUE);
        List<FinancialStatementLine> expenses = lines(trialBalance, AccountType.EXPENSE);
        BigDecimal totalRevenue = total(revenue);
        BigDecimal totalExpenses = total(expenses);
        BigDecimal netIncome = totalRevenue.subtract(totalExpenses).setScale(2, RoundingMode.HALF_UP);

        return new FinancialStatementsReport(
                fromDate,
                toDate,
                assets,
                liabilities,
                equity,
                revenue,
                expenses,
                total(assets),
                total(liabilities),
                total(equity),
                totalRevenue,
                totalExpenses,
                netIncome,
                trialBalance.balanced(),
                Instant.now()
        );
    }

    @Transactional(readOnly = true)
    public TaxRecapReport taxRecap(LocalDate fromDate, LocalDate toDate, BigDecimal incomeTaxRate) {
        FinancialStatementsReport statements = statements(fromDate, toDate);
        BigDecimal rate = incomeTaxRate == null ? new BigDecimal("0.22") : incomeTaxRate.setScale(4, RoundingMode.HALF_UP);
        if (rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException("TAX_RATE_INVALID", "Tax rate must be between 0 and 1.");
        }
        BigDecimal taxableIncome = statements.netIncome().signum() > 0 ? statements.netIncome() : BigDecimal.ZERO.setScale(2);
        BigDecimal estimatedIncomeTax = taxableIncome.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        return new TaxRecapReport(
                fromDate,
                toDate,
                statements.totalRevenue(),
                statements.totalExpenses(),
                taxableIncome,
                rate,
                estimatedIncomeTax,
                Instant.now()
        );
    }

    private static List<FinancialStatementLine> lines(TrialBalanceReport report, AccountType accountType) {
        return report.lines().stream()
                .filter(line -> line.accountType() == accountType)
                .map(FinancialStatementsApplicationService::line)
                .filter(line -> line.amount().signum() != 0)
                .toList();
    }

    private static FinancialStatementLine line(TrialBalanceLine line) {
        BigDecimal amount = switch (line.accountType()) {
            case ASSET, EXPENSE -> line.debitBalance().subtract(line.creditBalance());
            case LIABILITY, EQUITY, REVENUE -> line.creditBalance().subtract(line.debitBalance());
        };
        return new FinancialStatementLine(line.accountId(), line.accountCode(), line.accountName(), line.accountType(), amount.setScale(2, RoundingMode.HALF_UP));
    }

    private static BigDecimal total(List<FinancialStatementLine> lines) {
        return lines.stream()
                .map(FinancialStatementLine::amount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
