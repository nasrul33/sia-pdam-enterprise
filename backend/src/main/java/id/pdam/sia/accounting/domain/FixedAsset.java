package id.pdam.sia.accounting.domain;

import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "fixed_assets")
public class FixedAsset extends BaseEntity {
    @Column(nullable = false, unique = true, length = 64)
    private String assetCode;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false)
    private LocalDate acquisitionDate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal acquisitionCost;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal salvageValue;

    @Column(nullable = false)
    private int usefulLifeMonths;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FixedAssetDepreciationMethod depreciationMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FixedAssetStatus status;

    @Column(nullable = false)
    private UUID assetAccountId;

    @Column(nullable = false)
    private UUID accumulatedDepreciationAccountId;

    @Column(nullable = false)
    private UUID depreciationExpenseAccountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal accumulatedDepreciation;

    @Column(nullable = false)
    private UUID registeredJournalEntryId;

    private Instant disposedAt;

    @Column(columnDefinition = "TEXT")
    private String disposalReason;

    private UUID disposalJournalEntryId;

    protected FixedAsset() {
    }

    public FixedAsset(
            String assetCode,
            String name,
            LocalDate acquisitionDate,
            BigDecimal acquisitionCost,
            BigDecimal salvageValue,
            int usefulLifeMonths,
            FixedAssetDepreciationMethod depreciationMethod,
            UUID assetAccountId,
            UUID accumulatedDepreciationAccountId,
            UUID depreciationExpenseAccountId,
            UUID registeredJournalEntryId
    ) {
        if (acquisitionDate == null) {
            throw new BusinessException("FIXED_ASSET_ACQUISITION_DATE_REQUIRED", "Fixed asset acquisition date is required.");
        }
        if (usefulLifeMonths < 1) {
            throw new BusinessException("FIXED_ASSET_USEFUL_LIFE_INVALID", "Fixed asset useful life must be greater than zero.");
        }
        if (depreciationMethod == null) {
            throw new BusinessException("FIXED_ASSET_METHOD_REQUIRED", "Fixed asset depreciation method is required.");
        }
        if (assetAccountId == null || accumulatedDepreciationAccountId == null || depreciationExpenseAccountId == null) {
            throw new BusinessException("FIXED_ASSET_ACCOUNT_REQUIRED", "Fixed asset accounts are required.");
        }
        if (registeredJournalEntryId == null) {
            throw new BusinessException("FIXED_ASSET_REGISTERED_JOURNAL_REQUIRED", "Fixed asset registration journal is required.");
        }
        BigDecimal normalizedCost = requirePositive(acquisitionCost, "FIXED_ASSET_COST_REQUIRED", "Fixed asset acquisition cost is required.");
        BigDecimal normalizedSalvage = normalizeMoney(salvageValue);
        if (normalizedSalvage.signum() < 0 || normalizedSalvage.compareTo(normalizedCost) >= 0) {
            throw new BusinessException("FIXED_ASSET_SALVAGE_INVALID", "Fixed asset salvage value must be lower than cost.");
        }
        this.assetCode = require(assetCode, "FIXED_ASSET_CODE_REQUIRED", "Fixed asset code is required.");
        this.name = require(name, "FIXED_ASSET_NAME_REQUIRED", "Fixed asset name is required.");
        this.acquisitionDate = acquisitionDate;
        this.acquisitionCost = normalizedCost;
        this.salvageValue = normalizedSalvage;
        this.usefulLifeMonths = usefulLifeMonths;
        this.depreciationMethod = depreciationMethod;
        this.assetAccountId = assetAccountId;
        this.accumulatedDepreciationAccountId = accumulatedDepreciationAccountId;
        this.depreciationExpenseAccountId = depreciationExpenseAccountId;
        this.registeredJournalEntryId = registeredJournalEntryId;
        this.accumulatedDepreciation = BigDecimal.ZERO.setScale(2);
        this.status = FixedAssetStatus.ACTIVE;
    }

    public BigDecimal nextDepreciationAmount() {
        ensureActive();
        BigDecimal depreciableRemaining = acquisitionCost.subtract(salvageValue).subtract(accumulatedDepreciation).setScale(2, RoundingMode.HALF_UP);
        if (depreciableRemaining.signum() <= 0) {
            throw new BusinessException("FIXED_ASSET_FULLY_DEPRECIATED", "Fixed asset is already fully depreciated.");
        }
        BigDecimal amount;
        if (depreciationMethod == FixedAssetDepreciationMethod.DECLINING_BALANCE) {
            amount = acquisitionCost.subtract(accumulatedDepreciation)
                    .multiply(BigDecimal.valueOf(2))
                    .divide(BigDecimal.valueOf(usefulLifeMonths), 2, RoundingMode.HALF_UP);
        } else {
            amount = acquisitionCost.subtract(salvageValue)
                    .divide(BigDecimal.valueOf(usefulLifeMonths), 2, RoundingMode.HALF_UP);
        }
        if (amount.compareTo(depreciableRemaining) > 0) {
            return depreciableRemaining;
        }
        if (amount.signum() <= 0) {
            return depreciableRemaining;
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    public void postDepreciation(BigDecimal amount) {
        ensureActive();
        BigDecimal normalized = requirePositive(amount, "FIXED_ASSET_DEPRECIATION_AMOUNT_REQUIRED", "Fixed asset depreciation amount is required.");
        BigDecimal maximum = acquisitionCost.subtract(salvageValue).subtract(accumulatedDepreciation).setScale(2, RoundingMode.HALF_UP);
        if (normalized.compareTo(maximum) > 0) {
            throw new BusinessException("FIXED_ASSET_DEPRECIATION_EXCEEDS_BOOK_VALUE", "Fixed asset depreciation exceeds remaining depreciable value.");
        }
        accumulatedDepreciation = accumulatedDepreciation.add(normalized).setScale(2, RoundingMode.HALF_UP);
    }

    public void dispose(UUID disposalJournalEntryId, String reason) {
        ensureActive();
        if (disposalJournalEntryId == null) {
            throw new BusinessException("FIXED_ASSET_DISPOSAL_JOURNAL_REQUIRED", "Fixed asset disposal journal is required.");
        }
        this.status = FixedAssetStatus.DISPOSED;
        this.disposalJournalEntryId = disposalJournalEntryId;
        this.disposedAt = Instant.now();
        this.disposalReason = require(reason, "FIXED_ASSET_DISPOSAL_REASON_REQUIRED", "Fixed asset disposal reason is required.");
    }

    public BigDecimal netBookValue() {
        return acquisitionCost.subtract(accumulatedDepreciation).setScale(2, RoundingMode.HALF_UP);
    }

    private void ensureActive() {
        if (status != FixedAssetStatus.ACTIVE) {
            throw new BusinessException("FIXED_ASSET_NOT_ACTIVE", "Fixed asset must be active.");
        }
    }

    private static BigDecimal requirePositive(BigDecimal value, String code, String message) {
        BigDecimal normalized = normalizeMoney(value);
        if (normalized.signum() <= 0) {
            throw new BusinessException(code, message);
        }
        return normalized;
    }

    private static BigDecimal normalizeMoney(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String require(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    public String getAssetCode() {
        return assetCode;
    }

    public String getName() {
        return name;
    }

    public LocalDate getAcquisitionDate() {
        return acquisitionDate;
    }

    public BigDecimal getAcquisitionCost() {
        return acquisitionCost;
    }

    public BigDecimal getSalvageValue() {
        return salvageValue;
    }

    public int getUsefulLifeMonths() {
        return usefulLifeMonths;
    }

    public FixedAssetDepreciationMethod getDepreciationMethod() {
        return depreciationMethod;
    }

    public FixedAssetStatus getStatus() {
        return status;
    }

    public UUID getAssetAccountId() {
        return assetAccountId;
    }

    public UUID getAccumulatedDepreciationAccountId() {
        return accumulatedDepreciationAccountId;
    }

    public UUID getDepreciationExpenseAccountId() {
        return depreciationExpenseAccountId;
    }

    public BigDecimal getAccumulatedDepreciation() {
        return accumulatedDepreciation;
    }

    public UUID getRegisteredJournalEntryId() {
        return registeredJournalEntryId;
    }

    public Instant getDisposedAt() {
        return disposedAt;
    }

    public String getDisposalReason() {
        return disposalReason;
    }

    public UUID getDisposalJournalEntryId() {
        return disposalJournalEntryId;
    }
}
