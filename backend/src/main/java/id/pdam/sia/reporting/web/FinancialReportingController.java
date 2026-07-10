package id.pdam.sia.reporting.web;

import id.pdam.sia.reporting.application.FinancialStatementsApplicationService;
import id.pdam.sia.reporting.application.FinancialStatementsReport;
import id.pdam.sia.reporting.application.TaxRecapReport;
import id.pdam.sia.shared.security.Permissions;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;

@Validated
@RestController
@RequestMapping("/api/reports")
public class FinancialReportingController {
    private final FinancialStatementsApplicationService service;

    public FinancialReportingController(FinancialStatementsApplicationService service) {
        this.service = service;
    }

    @GetMapping("/financial-statements")
    @PreAuthorize(Permissions.FINANCIAL_REPORT_READ)
    public FinancialStatementsReport financialStatements(
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    ) {
        return service.statements(fromDate, toDate);
    }

    @GetMapping("/tax-recap")
    @PreAuthorize(Permissions.FINANCIAL_REPORT_READ)
    public TaxRecapReport taxRecap(
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) BigDecimal incomeTaxRate
    ) {
        return service.taxRecap(fromDate, toDate, incomeTaxRate);
    }
}
