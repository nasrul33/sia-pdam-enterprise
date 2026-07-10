package id.pdam.sia.receivable.web;

import id.pdam.sia.accounting.domain.JournalEntry;
import id.pdam.sia.receivable.application.ReceivableBlueprintApplicationService;
import id.pdam.sia.receivable.domain.InstallmentItem;
import id.pdam.sia.receivable.domain.InstallmentItemStatus;
import id.pdam.sia.receivable.domain.InstallmentPlan;
import id.pdam.sia.receivable.domain.InstallmentPlanStatus;
import id.pdam.sia.shared.security.Permissions;
import id.pdam.sia.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/receivables")
public class ReceivableBlueprintController {
    private final ReceivableBlueprintApplicationService service;

    public ReceivableBlueprintController(ReceivableBlueprintApplicationService service) {
        this.service = service;
    }

    @GetMapping("/installment-plans")
    @PreAuthorize(Permissions.INSTALLMENT_MANAGE)
    public PageResponse<InstallmentPlanSummaryResponse> listPlans(
            @RequestParam(required = false) InstallmentPlanStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        return PageResponse.from(service.listPlans(status, page, size).map(InstallmentPlanSummaryResponse::from));
    }

    @GetMapping("/installment-plans/{planId}")
    @PreAuthorize(Permissions.INSTALLMENT_MANAGE)
    public InstallmentPlanResponse getPlan(@PathVariable UUID planId) {
        return InstallmentPlanResponse.from(service.getPlan(planId));
    }

    @PostMapping("/installment-plans")
    @PreAuthorize(Permissions.INSTALLMENT_MANAGE)
    public InstallmentPlanResponse createPlan(@Valid @RequestBody CreateInstallmentPlanRequest request, Principal principal) {
        return InstallmentPlanResponse.from(service.createPlan(request.toCommand(), actor(principal)));
    }

    @PostMapping("/dunning/run")
    @PreAuthorize(Permissions.COLLECTION_ACTION_CREATE)
    public DunningRunResponse runDunning(@Valid @RequestBody RunDunningRequest request, Principal principal) {
        ReceivableBlueprintApplicationService.DunningRunResult result = service.runDunning(request.toCommand(), actor(principal));
        return new DunningRunResponse(result.asOfDate(), result.candidateInvoices(), result.createdActions(), result.skippedInvoices());
    }

    @PostMapping("/allowance")
    @PreAuthorize(Permissions.ALLOWANCE_POST)
    public AllowancePostingResponse postAllowance(@Valid @RequestBody PostAllowanceRequest request, Principal principal) {
        JournalEntry journal = service.postAllowance(request.toCommand(), actor(principal));
        return new AllowancePostingResponse(
                journal.getId(),
                journal.getJournalNumber(),
                journal.getStatus().name(),
                journal.getPostedAt(),
                journal.getSourceModule(),
                journal.getSourceDocumentNumber()
        );
    }

    private static String actor(Principal principal) {
        return principal == null ? "system" : principal.getName();
    }

    public record CreateInstallmentPlanRequest(
            @NotNull UUID invoiceId,
            @Min(1) @Max(36) int installmentCount,
            @NotNull LocalDate firstDueDate,
            String notes,
            @NotBlank String reason
    ) {
        private ReceivableBlueprintApplicationService.CreateInstallmentPlanCommand toCommand() {
            return new ReceivableBlueprintApplicationService.CreateInstallmentPlanCommand(
                    invoiceId,
                    installmentCount,
                    firstDueDate,
                    notes,
                    reason
            );
        }
    }

    public record RunDunningRequest(LocalDate asOfDate) {
        private ReceivableBlueprintApplicationService.RunDunningCommand toCommand() {
            return new ReceivableBlueprintApplicationService.RunDunningCommand(asOfDate);
        }
    }

    public record PostAllowanceRequest(
            @NotBlank String period,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @NotNull UUID expenseAccountId,
            @NotNull UUID allowanceAccountId,
            @NotBlank String reason
    ) {
        private ReceivableBlueprintApplicationService.PostAllowanceCommand toCommand() {
            return new ReceivableBlueprintApplicationService.PostAllowanceCommand(period, amount, expenseAccountId, allowanceAccountId, reason);
        }
    }

    public record InstallmentPlanSummaryResponse(
            UUID id,
            UUID invoiceId,
            String planNumber,
            InstallmentPlanStatus status,
            BigDecimal totalAmount,
            int installmentCount,
            String createdBy,
            String approvedBy,
            Instant approvedAt,
            Instant completedAt,
            String notes,
            Instant createdAt,
            Instant updatedAt
    ) {
        private static InstallmentPlanSummaryResponse from(InstallmentPlan plan) {
            return new InstallmentPlanSummaryResponse(
                    plan.getId(),
                    plan.getInvoiceId(),
                    plan.getPlanNumber(),
                    plan.getStatus(),
                    plan.getTotalAmount(),
                    plan.getInstallmentCount(),
                    plan.getCreatedBy(),
                    plan.getApprovedBy(),
                    plan.getApprovedAt(),
                    plan.getCompletedAt(),
                    plan.getNotes(),
                    plan.getCreatedAt(),
                    plan.getUpdatedAt()
            );
        }
    }

    public record InstallmentPlanResponse(
            UUID id,
            UUID invoiceId,
            String planNumber,
            InstallmentPlanStatus status,
            BigDecimal totalAmount,
            int installmentCount,
            String createdBy,
            String approvedBy,
            Instant approvedAt,
            Instant completedAt,
            String notes,
            List<InstallmentItemResponse> items,
            Instant createdAt,
            Instant updatedAt
    ) {
        private static InstallmentPlanResponse from(ReceivableBlueprintApplicationService.InstallmentPlanResult result) {
            InstallmentPlan plan = result.plan();
            return new InstallmentPlanResponse(
                    plan.getId(),
                    plan.getInvoiceId(),
                    plan.getPlanNumber(),
                    plan.getStatus(),
                    plan.getTotalAmount(),
                    plan.getInstallmentCount(),
                    plan.getCreatedBy(),
                    plan.getApprovedBy(),
                    plan.getApprovedAt(),
                    plan.getCompletedAt(),
                    plan.getNotes(),
                    result.items().stream().map(InstallmentItemResponse::from).toList(),
                    plan.getCreatedAt(),
                    plan.getUpdatedAt()
            );
        }
    }

    public record InstallmentItemResponse(
            UUID id,
            UUID planId,
            int installmentNumber,
            LocalDate dueDate,
            BigDecimal amount,
            BigDecimal paidAmount,
            InstallmentItemStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        private static InstallmentItemResponse from(InstallmentItem item) {
            return new InstallmentItemResponse(
                    item.getId(),
                    item.getPlanId(),
                    item.getInstallmentNumber(),
                    item.getDueDate(),
                    item.getAmount(),
                    item.getPaidAmount(),
                    item.getStatus(),
                    item.getCreatedAt(),
                    item.getUpdatedAt()
            );
        }
    }

    public record DunningRunResponse(LocalDate asOfDate, int candidateInvoices, int createdActions, int skippedInvoices) {
    }

    public record AllowancePostingResponse(
            UUID journalEntryId,
            String journalNumber,
            String status,
            Instant postedAt,
            String sourceModule,
            String sourceDocumentNumber
    ) {
    }
}
