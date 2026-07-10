package id.pdam.sia.reporting.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TaxRecapReport(
        LocalDate fromDate,
        LocalDate toDate,
        BigDecimal grossRevenue,
        BigDecimal deductibleExpenses,
        BigDecimal taxableIncome,
        BigDecimal incomeTaxRate,
        BigDecimal estimatedIncomeTax,
        Instant generatedAt
) {
}
