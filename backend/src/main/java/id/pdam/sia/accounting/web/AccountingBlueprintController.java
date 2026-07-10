package id.pdam.sia.accounting.web;

import id.pdam.sia.accounting.application.AccountingBlueprintApplicationService;
import id.pdam.sia.accounting.domain.FixedAsset;
import id.pdam.sia.accounting.domain.FixedAssetDepreciation;
import id.pdam.sia.accounting.domain.FixedAssetDepreciationMethod;
import id.pdam.sia.accounting.domain.FixedAssetStatus;
import id.pdam.sia.accounting.domain.JournalEntry;
import id.pdam.sia.accounting.domain.Payable;
import id.pdam.sia.accounting.domain.PayableStatus;
import id.pdam.sia.accounting.domain.Supplier;
import id.pdam.sia.accounting.domain.SupplierStatus;
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
@RequestMapping("/api/accounting")
public class AccountingBlueprintController {
    private final AccountingBlueprintApplicationService service;

    public AccountingBlueprintController(AccountingBlueprintApplicationService service) {
        this.service = service;
    }

    @GetMapping("/suppliers")
    @PreAuthorize(Permissions.SUPPLIER_MANAGE)
    public PageResponse<SupplierResponse> listSuppliers(
            @RequestParam(required = false) SupplierStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        return PageResponse.from(service.listSuppliers(status, page, size).map(SupplierResponse::from));
    }

    @PostMapping("/suppliers")
    @PreAuthorize(Permissions.SUPPLIER_MANAGE)
    public SupplierResponse createSupplier(@Valid @RequestBody CreateSupplierRequest request, Principal principal) {
        return SupplierResponse.from(service.createSupplier(request.toCommand(), actor(principal)));
    }

    @GetMapping("/payables")
    @PreAuthorize(Permissions.PAYABLE_RECORD)
    public PageResponse<PayableResponse> listPayables(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) PayableStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        return PageResponse.from(service.listPayables(period, status, page, size).map(PayableResponse::from));
    }

    @PostMapping("/payables")
    @PreAuthorize(Permissions.PAYABLE_RECORD)
    public PayableResponse recordPayable(@Valid @RequestBody RecordPayableRequest request, Principal principal) {
        return PayableResponse.from(service.recordPayable(request.toCommand(), actor(principal)));
    }

    @PostMapping("/payables/{payableId}/settle")
    @PreAuthorize(Permissions.PAYABLE_SETTLE)
    public PayableResponse settlePayable(
            @PathVariable UUID payableId,
            @Valid @RequestBody SettlePayableRequest request,
            Principal principal
    ) {
        return PayableResponse.from(service.settlePayable(payableId, request.toCommand(), actor(principal)));
    }

    @GetMapping("/fixed-assets")
    @PreAuthorize(Permissions.ASSET_MANAGE)
    public PageResponse<FixedAssetResponse> listFixedAssets(
            @RequestParam(required = false) FixedAssetStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        return PageResponse.from(service.listFixedAssets(status, page, size).map(FixedAssetResponse::from));
    }

    @PostMapping("/fixed-assets")
    @PreAuthorize(Permissions.ASSET_MANAGE)
    public FixedAssetResponse registerFixedAsset(@Valid @RequestBody RegisterFixedAssetRequest request, Principal principal) {
        return FixedAssetResponse.from(service.registerFixedAsset(request.toCommand(), actor(principal)));
    }

    @PostMapping("/fixed-assets/{assetId}/depreciations")
    @PreAuthorize(Permissions.ASSET_DEPRECIATE)
    public FixedAssetDepreciationResponse postDepreciation(
            @PathVariable UUID assetId,
            @Valid @RequestBody PostAssetDepreciationRequest request,
            Principal principal
    ) {
        return FixedAssetDepreciationResponse.from(service.postFixedAssetDepreciation(assetId, request.toCommand(), actor(principal)));
    }

    @PostMapping("/fixed-assets/{assetId}/dispose")
    @PreAuthorize(Permissions.ASSET_MANAGE)
    public FixedAssetResponse disposeFixedAsset(
            @PathVariable UUID assetId,
            @Valid @RequestBody DisposeFixedAssetRequest request,
            Principal principal
    ) {
        return FixedAssetResponse.from(service.disposeFixedAsset(assetId, request.toCommand(), actor(principal)));
    }

    @PostMapping("/journals/{journalId}/reverse")
    @PreAuthorize(Permissions.JOURNAL_REVERSE)
    public JournalResponse reverseJournal(
            @PathVariable UUID journalId,
            @Valid @RequestBody ReverseJournalRequest request,
            Principal principal
    ) {
        return JournalResponse.from(service.reverseJournal(journalId, request.toCommand(), actor(principal)));
    }

    @PostMapping("/opening-balances")
    @PreAuthorize(Permissions.OPENING_BALANCE_POST)
    public JournalResponse postOpeningBalance(@Valid @RequestBody PostOpeningBalanceRequest request, Principal principal) {
        return JournalResponse.from(service.postOpeningBalance(request.toCommand(), actor(principal)));
    }

    @PostMapping("/closing-entries")
    @PreAuthorize(Permissions.CLOSING_ENTRY_POST)
    public JournalResponse postClosingEntries(@Valid @RequestBody PostClosingEntryRequest request, Principal principal) {
        return JournalResponse.from(service.postClosingEntries(request.toCommand(), actor(principal)));
    }

    private static String actor(Principal principal) {
        return principal == null ? "system" : principal.getName();
    }

    public record CreateSupplierRequest(
            @NotBlank String code,
            @NotBlank String name,
            String contactName,
            String phoneNumber,
            @NotBlank String reason
    ) {
        private AccountingBlueprintApplicationService.CreateSupplierCommand toCommand() {
            return new AccountingBlueprintApplicationService.CreateSupplierCommand(code, name, contactName, phoneNumber, reason);
        }
    }

    public record RecordPayableRequest(
            @NotNull UUID supplierId,
            @NotBlank String payableNumber,
            String supplierReference,
            @NotBlank String period,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @NotBlank String description,
            @NotNull UUID debitAccountId,
            @NotNull UUID payableAccountId,
            @NotBlank String reason
    ) {
        private AccountingBlueprintApplicationService.RecordPayableCommand toCommand() {
            return new AccountingBlueprintApplicationService.RecordPayableCommand(
                    supplierId,
                    payableNumber,
                    supplierReference,
                    period,
                    amount,
                    description,
                    debitAccountId,
                    payableAccountId,
                    reason
            );
        }
    }

    public record SettlePayableRequest(
            @NotNull UUID payableAccountId,
            @NotNull UUID cashAccountId,
            @NotBlank String reason
    ) {
        private AccountingBlueprintApplicationService.SettlePayableCommand toCommand() {
            return new AccountingBlueprintApplicationService.SettlePayableCommand(payableAccountId, cashAccountId, reason);
        }
    }

    public record RegisterFixedAssetRequest(
            @NotBlank String assetCode,
            @NotBlank String name,
            @NotBlank String period,
            @NotNull LocalDate acquisitionDate,
            @NotNull @DecimalMin(value = "0.01") BigDecimal acquisitionCost,
            @NotNull @DecimalMin(value = "0.00") BigDecimal salvageValue,
            @Min(1) int usefulLifeMonths,
            @NotNull FixedAssetDepreciationMethod depreciationMethod,
            @NotNull UUID assetAccountId,
            @NotNull UUID creditAccountId,
            @NotNull UUID accumulatedDepreciationAccountId,
            @NotNull UUID depreciationExpenseAccountId,
            @NotBlank String reason
    ) {
        private AccountingBlueprintApplicationService.RegisterFixedAssetCommand toCommand() {
            return new AccountingBlueprintApplicationService.RegisterFixedAssetCommand(
                    assetCode,
                    name,
                    period,
                    acquisitionDate,
                    acquisitionCost,
                    salvageValue,
                    usefulLifeMonths,
                    depreciationMethod,
                    assetAccountId,
                    creditAccountId,
                    accumulatedDepreciationAccountId,
                    depreciationExpenseAccountId,
                    reason
            );
        }
    }

    public record PostAssetDepreciationRequest(
            @NotBlank String period,
            @DecimalMin(value = "0.01") BigDecimal amount,
            @NotBlank String reason
    ) {
        private AccountingBlueprintApplicationService.PostAssetDepreciationCommand toCommand() {
            return new AccountingBlueprintApplicationService.PostAssetDepreciationCommand(period, amount, reason);
        }
    }

    public record DisposeFixedAssetRequest(
            @NotBlank String period,
            UUID lossAccountId,
            @NotBlank String reason
    ) {
        private AccountingBlueprintApplicationService.DisposeFixedAssetCommand toCommand() {
            return new AccountingBlueprintApplicationService.DisposeFixedAssetCommand(period, lossAccountId, reason);
        }
    }

    public record ReverseJournalRequest(@NotBlank String period, @NotBlank String reason) {
        private AccountingBlueprintApplicationService.ReverseJournalCommand toCommand() {
            return new AccountingBlueprintApplicationService.ReverseJournalCommand(period, reason);
        }
    }

    public record PostOpeningBalanceRequest(
            @NotBlank String period,
            @NotEmpty @Size(min = 2, max = 200) List<@Valid PostOpeningBalanceLineRequest> lines,
            @NotBlank String reason
    ) {
        private AccountingBlueprintApplicationService.PostOpeningBalanceCommand toCommand() {
            return new AccountingBlueprintApplicationService.PostOpeningBalanceCommand(
                    period,
                    lines.stream().map(PostOpeningBalanceLineRequest::toCommand).toList(),
                    reason
            );
        }
    }

    public record PostOpeningBalanceLineRequest(
            @NotNull UUID accountId,
            @NotNull @DecimalMin(value = "0.00") BigDecimal debit,
            @NotNull @DecimalMin(value = "0.00") BigDecimal credit,
            @NotBlank String description
    ) {
        private AccountingBlueprintApplicationService.PostOpeningBalanceLineCommand toCommand() {
            return new AccountingBlueprintApplicationService.PostOpeningBalanceLineCommand(accountId, debit, credit, description);
        }
    }

    public record PostClosingEntryRequest(
            @NotBlank String period,
            @NotNull UUID retainedEarningsAccountId,
            @NotBlank String reason
    ) {
        private AccountingBlueprintApplicationService.PostClosingEntryCommand toCommand() {
            return new AccountingBlueprintApplicationService.PostClosingEntryCommand(period, retainedEarningsAccountId, reason);
        }
    }

    public record SupplierResponse(
            UUID id,
            String code,
            String name,
            String contactName,
            String phoneNumber,
            SupplierStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        private static SupplierResponse from(Supplier supplier) {
            return new SupplierResponse(
                    supplier.getId(),
                    supplier.getCode(),
                    supplier.getName(),
                    supplier.getContactName(),
                    supplier.getPhoneNumber(),
                    supplier.getStatus(),
                    supplier.getCreatedAt(),
                    supplier.getUpdatedAt()
            );
        }
    }

    public record PayableResponse(
            UUID id,
            UUID supplierId,
            String payableNumber,
            String supplierReference,
            String period,
            PayableStatus status,
            BigDecimal amount,
            String description,
            Instant recordedAt,
            String recordedBy,
            Instant paidAt,
            String paidBy,
            UUID recordedJournalEntryId,
            UUID settlementJournalEntryId,
            Instant createdAt,
            Instant updatedAt
    ) {
        private static PayableResponse from(Payable payable) {
            return new PayableResponse(
                    payable.getId(),
                    payable.getSupplierId(),
                    payable.getPayableNumber(),
                    payable.getSupplierReference(),
                    payable.getPeriod(),
                    payable.getStatus(),
                    payable.getAmount(),
                    payable.getDescription(),
                    payable.getRecordedAt(),
                    payable.getRecordedBy(),
                    payable.getPaidAt(),
                    payable.getPaidBy(),
                    payable.getRecordedJournalEntryId(),
                    payable.getSettlementJournalEntryId(),
                    payable.getCreatedAt(),
                    payable.getUpdatedAt()
            );
        }
    }

    public record FixedAssetResponse(
            UUID id,
            String assetCode,
            String name,
            LocalDate acquisitionDate,
            BigDecimal acquisitionCost,
            BigDecimal salvageValue,
            int usefulLifeMonths,
            FixedAssetDepreciationMethod depreciationMethod,
            FixedAssetStatus status,
            UUID assetAccountId,
            UUID accumulatedDepreciationAccountId,
            UUID depreciationExpenseAccountId,
            BigDecimal accumulatedDepreciation,
            BigDecimal netBookValue,
            UUID registeredJournalEntryId,
            Instant disposedAt,
            String disposalReason,
            UUID disposalJournalEntryId,
            Instant createdAt,
            Instant updatedAt
    ) {
        private static FixedAssetResponse from(FixedAsset asset) {
            return new FixedAssetResponse(
                    asset.getId(),
                    asset.getAssetCode(),
                    asset.getName(),
                    asset.getAcquisitionDate(),
                    asset.getAcquisitionCost(),
                    asset.getSalvageValue(),
                    asset.getUsefulLifeMonths(),
                    asset.getDepreciationMethod(),
                    asset.getStatus(),
                    asset.getAssetAccountId(),
                    asset.getAccumulatedDepreciationAccountId(),
                    asset.getDepreciationExpenseAccountId(),
                    asset.getAccumulatedDepreciation(),
                    asset.netBookValue(),
                    asset.getRegisteredJournalEntryId(),
                    asset.getDisposedAt(),
                    asset.getDisposalReason(),
                    asset.getDisposalJournalEntryId(),
                    asset.getCreatedAt(),
                    asset.getUpdatedAt()
            );
        }
    }

    public record FixedAssetDepreciationResponse(
            UUID id,
            UUID assetId,
            String period,
            BigDecimal amount,
            UUID journalEntryId,
            Instant postedAt,
            String postedBy,
            Instant createdAt,
            Instant updatedAt
    ) {
        private static FixedAssetDepreciationResponse from(FixedAssetDepreciation depreciation) {
            return new FixedAssetDepreciationResponse(
                    depreciation.getId(),
                    depreciation.getAssetId(),
                    depreciation.getPeriod(),
                    depreciation.getAmount(),
                    depreciation.getJournalEntryId(),
                    depreciation.getPostedAt(),
                    depreciation.getPostedBy(),
                    depreciation.getCreatedAt(),
                    depreciation.getUpdatedAt()
            );
        }
    }

    public record JournalResponse(
            UUID id,
            String journalNumber,
            UUID accountingPeriodId,
            String description,
            String status,
            Instant postedAt,
            String postedBy,
            String sourceModule,
            UUID sourceRecordId,
            String sourceDocumentNumber
    ) {
        private static JournalResponse from(JournalEntry journal) {
            return new JournalResponse(
                    journal.getId(),
                    journal.getJournalNumber(),
                    journal.getAccountingPeriodId(),
                    journal.getDescription(),
                    journal.getStatus().name(),
                    journal.getPostedAt(),
                    journal.getPostedBy(),
                    journal.getSourceModule(),
                    journal.getSourceRecordId(),
                    journal.getSourceDocumentNumber()
            );
        }
    }
}
