package id.pdam.sia.reporting.web;

import id.pdam.sia.reporting.application.BankReconciliationEvidenceReport;
import id.pdam.sia.reporting.application.BankReconciliationEvidenceReportApplicationService;
import id.pdam.sia.reporting.application.BankReconciliationReviewRegisterApplicationService;
import id.pdam.sia.reporting.application.BankReconciliationReviewRegisterEntry;
import id.pdam.sia.reporting.application.BankReconciliationReviewRegisterFilters;
import id.pdam.sia.reporting.application.PaymentReconciliationReviewStatus;
import id.pdam.sia.reporting.application.PostedLedgerReportApplicationService;
import id.pdam.sia.reporting.application.TrialBalanceReport;
import id.pdam.sia.shared.security.Permissions;
import id.pdam.sia.shared.web.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/reports")
public class ReportingController {
    private static final MediaType TEXT_CSV = MediaType.parseMediaType("text/csv");

    private final PostedLedgerReportApplicationService postedLedgerReportApplicationService;
    private final BankReconciliationEvidenceReportApplicationService bankReconciliationEvidenceReportApplicationService;
    private final BankReconciliationReviewRegisterApplicationService bankReconciliationReviewRegisterApplicationService;

    public ReportingController(
            PostedLedgerReportApplicationService postedLedgerReportApplicationService,
            BankReconciliationEvidenceReportApplicationService bankReconciliationEvidenceReportApplicationService,
            BankReconciliationReviewRegisterApplicationService bankReconciliationReviewRegisterApplicationService
    ) {
        this.postedLedgerReportApplicationService = postedLedgerReportApplicationService;
        this.bankReconciliationEvidenceReportApplicationService = bankReconciliationEvidenceReportApplicationService;
        this.bankReconciliationReviewRegisterApplicationService = bankReconciliationReviewRegisterApplicationService;
    }

    @GetMapping("/trial-balance")
    public TrialBalanceReport trialBalance(
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    ) {
        return postedLedgerReportApplicationService.trialBalance(fromDate, toDate);
    }

    @GetMapping("/payment-reconciliation-evidence/{sessionId}")
    @PreAuthorize(Permissions.PAYMENT_RECONCILE)
    public BankReconciliationEvidenceReport paymentReconciliationEvidence(@PathVariable UUID sessionId) {
        return bankReconciliationEvidenceReportApplicationService.evidenceReport(sessionId);
    }

    @GetMapping(value = "/payment-reconciliation-evidence/{sessionId}/export", produces = "text/csv")
    @PreAuthorize(Permissions.PAYMENT_RECONCILE)
    public ResponseEntity<String> exportPaymentReconciliationEvidence(@PathVariable UUID sessionId) {
        BankReconciliationEvidenceReport report = bankReconciliationEvidenceReportApplicationService.evidenceReport(sessionId);
        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("payment-reconciliation-evidence-" + report.sessionNumber() + ".csv")
                        .build()
                        .toString())
                .body(bankReconciliationEvidenceReportApplicationService.evidenceCsv(report));
    }

    @GetMapping("/payment-reconciliation-review-register")
    @PreAuthorize(Permissions.PAYMENT_RECONCILE)
    public PageResponse<BankReconciliationReviewRegisterEntry> paymentReconciliationReviewRegister(
            @RequestParam(required = false) PaymentReconciliationReviewStatus signOffStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant completedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant completedTo,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size
    ) {
        return PageResponse.from(bankReconciliationReviewRegisterApplicationService.reviewRegister(
                new BankReconciliationReviewRegisterFilters(signOffStatus, completedFrom, completedTo),
                page,
                size
        ));
    }

    @GetMapping(value = "/payment-reconciliation-review-register/export", produces = "text/csv")
    @PreAuthorize(Permissions.PAYMENT_RECONCILE)
    public ResponseEntity<String> exportPaymentReconciliationReviewRegister(
            @RequestParam(required = false) PaymentReconciliationReviewStatus signOffStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant completedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant completedTo
    ) {
        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("payment-reconciliation-review-register.csv")
                        .build()
                        .toString())
                .body(bankReconciliationReviewRegisterApplicationService.reviewRegisterCsv(
                        new BankReconciliationReviewRegisterFilters(signOffStatus, completedFrom, completedTo)
                ));
    }
}
