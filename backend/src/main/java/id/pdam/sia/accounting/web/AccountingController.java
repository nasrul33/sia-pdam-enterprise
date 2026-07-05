package id.pdam.sia.accounting.web;

import id.pdam.sia.accounting.application.AccountingApplicationService;
import id.pdam.sia.accounting.domain.JournalStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api")
public class AccountingController {
    private final AccountingApplicationService accountingApplicationService;

    public AccountingController(AccountingApplicationService accountingApplicationService) {
        this.accountingApplicationService = accountingApplicationService;
    }

    @GetMapping("/accounts")
    public PageResponse<AccountResponse> listAccounts(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        return PageResponse.from(accountingApplicationService.listAccounts(page, size).map(AccountResponse::from));
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public AccountResponse createAccount(@Valid @RequestBody CreateAccountRequest request, Principal principal) {
        return AccountResponse.from(accountingApplicationService.createAccount(request, actor(principal)));
    }

    @GetMapping("/accounting-periods")
    public PageResponse<AccountingPeriodResponse> listAccountingPeriods(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        return PageResponse.from(
                accountingApplicationService.listAccountingPeriods(page, size).map(AccountingPeriodResponse::from)
        );
    }

    @PostMapping("/accounting-periods")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public AccountingPeriodResponse createAccountingPeriod(
            @Valid @RequestBody CreateAccountingPeriodRequest request,
            Principal principal
    ) {
        return AccountingPeriodResponse.from(accountingApplicationService.createAccountingPeriod(request, actor(principal)));
    }

    @PostMapping("/accounting-periods/{periodId}/start-closing-review")
    @PreAuthorize("isAuthenticated()")
    public AccountingPeriodResponse startClosingReview(
            @PathVariable UUID periodId,
            @Valid @RequestBody WorkflowReasonRequest request,
            Principal principal
    ) {
        return AccountingPeriodResponse.from(
                accountingApplicationService.startClosingReview(periodId, request.reason(), actor(principal))
        );
    }

    @PostMapping("/accounting-periods/{periodId}/lock")
    @PreAuthorize("isAuthenticated()")
    public AccountingPeriodResponse lockPeriod(
            @PathVariable UUID periodId,
            @Valid @RequestBody WorkflowReasonRequest request,
            Principal principal
    ) {
        return AccountingPeriodResponse.from(accountingApplicationService.lockPeriod(periodId, request.reason(), actor(principal)));
    }

    @GetMapping("/journals")
    public PageResponse<JournalSummaryResponse> listJournals(
            @RequestParam(required = false) UUID accountingPeriodId,
            @RequestParam(required = false) JournalStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        return PageResponse.from(
                accountingApplicationService.listJournals(accountingPeriodId, status, page, size).map(JournalSummaryResponse::from)
        );
    }

    @GetMapping("/journals/{journalId}")
    public JournalResponse getJournal(@PathVariable UUID journalId) {
        return JournalResponse.from(accountingApplicationService.getJournal(journalId));
    }

    @PostMapping("/journals")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public JournalResponse createJournal(@Valid @RequestBody CreateJournalRequest request, Principal principal) {
        return JournalResponse.from(accountingApplicationService.createJournal(request, actor(principal)));
    }

    @PostMapping("/journals/{journalId}/post")
    @PreAuthorize("isAuthenticated()")
    public JournalResponse postJournal(
            @PathVariable UUID journalId,
            @Valid @RequestBody PostJournalRequest request,
            Principal principal
    ) {
        return JournalResponse.from(accountingApplicationService.postJournal(journalId, request.reason(), actor(principal)));
    }

    private static String actor(Principal principal) {
        return principal == null ? "system" : principal.getName();
    }
}
