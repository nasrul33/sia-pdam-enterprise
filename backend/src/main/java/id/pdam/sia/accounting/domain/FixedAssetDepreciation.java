package id.pdam.sia.accounting.domain;

import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fixed_asset_depreciations")
public class FixedAssetDepreciation extends BaseEntity {
    @Column(nullable = false)
    private UUID assetId;

    @Column(nullable = false, length = 7)
    private String period;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private UUID journalEntryId;

    @Column(nullable = false)
    private Instant postedAt;

    @Column(nullable = false, length = 128)
    private String postedBy;

    protected FixedAssetDepreciation() {
    }

    public FixedAssetDepreciation(UUID assetId, String period, BigDecimal amount, UUID journalEntryId, String postedBy) {
        if (assetId == null) {
            throw new BusinessException("FIXED_ASSET_DEPRECIATION_ASSET_REQUIRED", "Fixed asset depreciation asset is required.");
        }
        if (journalEntryId == null) {
            throw new BusinessException("FIXED_ASSET_DEPRECIATION_JOURNAL_REQUIRED", "Fixed asset depreciation journal is required.");
        }
        this.assetId = assetId;
        this.period = require(period, "FIXED_ASSET_DEPRECIATION_PERIOD_REQUIRED", "Fixed asset depreciation period is required.");
        this.amount = requirePositive(amount);
        this.journalEntryId = journalEntryId;
        this.postedAt = Instant.now();
        this.postedBy = require(postedBy, "FIXED_ASSET_DEPRECIATION_POSTED_BY_REQUIRED", "Fixed asset depreciation actor is required.");
    }

    private static BigDecimal requirePositive(BigDecimal value) {
        BigDecimal normalized = (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
        if (normalized.signum() <= 0) {
            throw new BusinessException("FIXED_ASSET_DEPRECIATION_AMOUNT_INVALID", "Fixed asset depreciation amount must be greater than zero.");
        }
        return normalized;
    }

    private static String require(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    public UUID getAssetId() {
        return assetId;
    }

    public String getPeriod() {
        return period;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public UUID getJournalEntryId() {
        return journalEntryId;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public String getPostedBy() {
        return postedBy;
    }
}
