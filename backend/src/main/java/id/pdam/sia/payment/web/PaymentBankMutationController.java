package id.pdam.sia.payment.web;

import id.pdam.sia.payment.application.PaymentBankMutationApplicationService;
import id.pdam.sia.payment.domain.BankMutation;
import id.pdam.sia.payment.domain.BankMutationStatus;
import id.pdam.sia.shared.security.Permissions;
import id.pdam.sia.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
@RequestMapping("/api/bank-mutations")
public class PaymentBankMutationController {
    private final PaymentBankMutationApplicationService service;

    public PaymentBankMutationController(PaymentBankMutationApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Permissions.PAYMENT_RECONCILE)
    public PageResponse<BankMutationResponse> listMutations(
            @RequestParam(required = false) BankMutationStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        return PageResponse.from(service.listMutations(status, page, size).map(BankMutationResponse::from));
    }

    @PostMapping("/import")
    @PreAuthorize(Permissions.BANK_MUTATION_IMPORT)
    public BankMutationImportResponse importMutations(
            @Valid @RequestBody ImportBankMutationsRequest request,
            Principal principal
    ) {
        PaymentBankMutationApplicationService.BankMutationImportResult result = service.importMutations(
                request.toCommand(),
                actor(principal)
        );
        return new BankMutationImportResponse(
                result.importedRows(),
                result.mutations().stream().map(BankMutationResponse::from).toList()
        );
    }

    @PostMapping("/reconcile-daily")
    @PreAuthorize(Permissions.BANK_MUTATION_RECONCILE)
    public BankMutationReconciliationResponse reconcileDaily(
            @Valid @RequestBody ReconcileBankDailyRequest request,
            Principal principal
    ) {
        PaymentBankMutationApplicationService.BankMutationReconciliationResult result = service.reconcileDaily(
                request.toCommand(),
                actor(principal)
        );
        return new BankMutationReconciliationResponse(result.sessionId(), result.totalRows(), result.matchedRows(), result.exceptionRows());
    }

    @PostMapping("/{mutationId}/resolve")
    @PreAuthorize(Permissions.BANK_MUTATION_RECONCILE)
    public BankMutationResponse resolveMutation(
            @PathVariable UUID mutationId,
            @Valid @RequestBody ResolveBankMutationRequest request,
            Principal principal
    ) {
        return BankMutationResponse.from(service.resolveMutation(mutationId, request.toCommand(), actor(principal)));
    }

    private static String actor(Principal principal) {
        return principal == null ? "system" : principal.getName();
    }

    public record ImportBankMutationsRequest(
            @NotBlank String sourceFilename,
            @NotBlank String bankAccountReference,
            @NotEmpty @Size(max = 1000) List<@Valid BankMutationRowRequest> rows
    ) {
        private PaymentBankMutationApplicationService.ImportBankMutationsCommand toCommand() {
            return new PaymentBankMutationApplicationService.ImportBankMutationsCommand(
                    sourceFilename,
                    bankAccountReference,
                    rows.stream().map(BankMutationRowRequest::toCommand).toList()
            );
        }
    }

    public record BankMutationRowRequest(
            @NotBlank String externalReference,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @NotNull Instant transactedAt,
            String channel,
            String description
    ) {
        private PaymentBankMutationApplicationService.BankMutationRowCommand toCommand() {
            return new PaymentBankMutationApplicationService.BankMutationRowCommand(
                    externalReference,
                    amount,
                    transactedAt,
                    channel,
                    description
            );
        }
    }

    public record ReconcileBankDailyRequest(
            @NotNull LocalDate date,
            String sourceFilename,
            @NotBlank String bankAccountReference
    ) {
        private PaymentBankMutationApplicationService.ReconcileBankDailyCommand toCommand() {
            return new PaymentBankMutationApplicationService.ReconcileBankDailyCommand(date, sourceFilename, bankAccountReference);
        }
    }

    public record ResolveBankMutationRequest(@NotBlank String reason) {
        private PaymentBankMutationApplicationService.ResolveBankMutationCommand toCommand() {
            return new PaymentBankMutationApplicationService.ResolveBankMutationCommand(reason);
        }
    }

    public record BankMutationImportResponse(int importedRows, List<BankMutationResponse> mutations) {
    }

    public record BankMutationReconciliationResponse(UUID sessionId, int totalRows, int matchedRows, int exceptionRows) {
    }

    public record BankMutationResponse(
            UUID id,
            String externalReference,
            String sourceFilename,
            String bankAccountReference,
            BigDecimal amount,
            Instant transactedAt,
            String channel,
            String description,
            BankMutationStatus status,
            UUID reconciliationSessionId,
            UUID matchedPaymentId,
            Instant matchedAt,
            Instant resolvedAt,
            String resolvedBy,
            String resolutionReason,
            Instant createdAt,
            Instant updatedAt
    ) {
        private static BankMutationResponse from(BankMutation mutation) {
            return new BankMutationResponse(
                    mutation.getId(),
                    mutation.getExternalReference(),
                    mutation.getSourceFilename(),
                    mutation.getBankAccountReference(),
                    mutation.getAmount(),
                    mutation.getTransactedAt(),
                    mutation.getChannel(),
                    mutation.getDescription(),
                    mutation.getStatus(),
                    mutation.getReconciliationSessionId(),
                    mutation.getMatchedPaymentId(),
                    mutation.getMatchedAt(),
                    mutation.getResolvedAt(),
                    mutation.getResolvedBy(),
                    mutation.getResolutionReason(),
                    mutation.getCreatedAt(),
                    mutation.getUpdatedAt()
            );
        }
    }
}
