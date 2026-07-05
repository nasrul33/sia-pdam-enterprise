package id.pdam.sia.reporting.web;

import id.pdam.sia.reporting.application.PostedLedgerReportApplicationService;
import id.pdam.sia.reporting.application.TrialBalanceReport;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Validated
@RestController
@RequestMapping("/api/reports")
public class ReportingController {
    private final PostedLedgerReportApplicationService postedLedgerReportApplicationService;

    public ReportingController(PostedLedgerReportApplicationService postedLedgerReportApplicationService) {
        this.postedLedgerReportApplicationService = postedLedgerReportApplicationService;
    }

    @GetMapping("/trial-balance")
    public TrialBalanceReport trialBalance(
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    ) {
        return postedLedgerReportApplicationService.trialBalance(fromDate, toDate);
    }
}
