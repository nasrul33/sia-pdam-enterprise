package id.pdam.sia.reporting.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record TrialBalanceReport(
        LocalDate fromDate,
        LocalDate toDate,
        List<TrialBalanceLine> lines,
        BigDecimal totalDebitBalance,
        BigDecimal totalCreditBalance,
        boolean balanced,
        Instant generatedAt
) {
    public TrialBalanceReport {
        lines = List.copyOf(lines);
    }
}
