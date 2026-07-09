package id.pdam.sia.billing.web;

import id.pdam.sia.billing.application.BillingBatchApplicationService;
import id.pdam.sia.billing.domain.BillingBatchStatus;
import id.pdam.sia.billing.domain.InvoiceStatus;
import id.pdam.sia.shared.security.Permissions;
import id.pdam.sia.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api")
public class BillingBatchController {
    private final BillingBatchApplicationService billingBatchApplicationService;

    public BillingBatchController(BillingBatchApplicationService billingBatchApplicationService) {
        this.billingBatchApplicationService = billingBatchApplicationService;
    }

    @GetMapping("/billing-batches")
    public PageResponse<BillingBatchResponse> listBatches(
            @RequestParam(required = false) @Pattern(regexp = "^\\d{4}-\\d{2}$") String period,
            @RequestParam(required = false) BillingBatchStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        return PageResponse.from(
                billingBatchApplicationService.listBatches(period, status, page, size).map(BillingBatchResponse::from)
        );
    }

    @GetMapping("/billing-batches/{batchId}")
    public BillingBatchResponse getBatch(@PathVariable UUID batchId) {
        return BillingBatchResponse.from(billingBatchApplicationService.getBatch(batchId));
    }

    @GetMapping("/billing-batches/{batchId}/invoices")
    public List<InvoiceResponse> listBatchInvoices(@PathVariable UUID batchId) {
        return billingBatchApplicationService.listBatchInvoices(batchId).stream()
                .map(InvoiceResponse::from)
                .toList();
    }

    @GetMapping("/billing-batches/{batchId}/issue-readiness")
    public BillingBatchIssueReadinessResponse getBatchIssueReadiness(@PathVariable UUID batchId) {
        return BillingBatchIssueReadinessResponse.from(billingBatchApplicationService.issueReadiness(batchId));
    }

    @PostMapping("/billing-batches/generate")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.BILLING_GENERATE)
    public BillingBatchGenerationResponse generateBatch(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody GenerateBillingBatchRequest request,
            Principal principal
    ) {
        return BillingBatchGenerationResponse.from(
                billingBatchApplicationService.generateBatch(request, idempotencyKey, actor(principal))
        );
    }

    @GetMapping("/invoices")
    public PageResponse<InvoiceResponse> listInvoices(
            @RequestParam(required = false) @Pattern(regexp = "^\\d{4}-\\d{2}$") String period,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        return PageResponse.from(
                billingBatchApplicationService.listInvoices(period, status, page, size).map(InvoiceResponse::from)
        );
    }

    @PostMapping("/invoices/{invoiceId}/issue")
    @PreAuthorize(Permissions.INVOICE_ISSUE)
    public InvoiceResponse issueInvoice(
            @PathVariable UUID invoiceId,
            @Valid @RequestBody IssueInvoiceRequest request,
            Principal principal
    ) {
        return InvoiceResponse.from(billingBatchApplicationService.issueInvoice(invoiceId, request, actor(principal)));
    }

    private static String actor(Principal principal) {
        return principal == null ? "system" : principal.getName();
    }
}
