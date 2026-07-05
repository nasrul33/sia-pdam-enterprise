package id.pdam.sia.receivable.domain;

import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Entity
@Table(name = "receivable_aging_snapshots")
public class ReceivableAgingSnapshot extends BaseEntity {
    @Column(nullable = false, unique = true, length = 7)
    private String period;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal currentAmount;

    @Column(name = "bucket_30_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal bucket30Amount;

    @Column(name = "bucket_60_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal bucket60Amount;

    @Column(name = "bucket_90_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal bucket90Amount;

    @Column(name = "bucket_over_90_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal bucketOver90Amount;

    @Column(nullable = false)
    private Instant generatedAt;

    protected ReceivableAgingSnapshot() {
    }

    public ReceivableAgingSnapshot(
            String period,
            BigDecimal currentAmount,
            BigDecimal bucket30Amount,
            BigDecimal bucket60Amount,
            BigDecimal bucket90Amount,
            BigDecimal bucketOver90Amount,
            Instant generatedAt
    ) {
        this.period = require(period, "RECEIVABLE_AGING_PERIOD_REQUIRED", "Receivable aging period is required.");
        replaceAmounts(currentAmount, bucket30Amount, bucket60Amount, bucket90Amount, bucketOver90Amount, generatedAt);
    }

    public void replaceAmounts(
            BigDecimal currentAmount,
            BigDecimal bucket30Amount,
            BigDecimal bucket60Amount,
            BigDecimal bucket90Amount,
            BigDecimal bucketOver90Amount,
            Instant generatedAt
    ) {
        if (generatedAt == null) {
            throw new BusinessException("RECEIVABLE_AGING_GENERATED_AT_REQUIRED", "Receivable aging generated timestamp is required.");
        }
        this.currentAmount = requireNonNegative(currentAmount, "RECEIVABLE_AGING_CURRENT_REQUIRED", "Receivable aging current amount is required.");
        this.bucket30Amount = requireNonNegative(bucket30Amount, "RECEIVABLE_AGING_BUCKET_30_REQUIRED", "Receivable aging 30-day amount is required.");
        this.bucket60Amount = requireNonNegative(bucket60Amount, "RECEIVABLE_AGING_BUCKET_60_REQUIRED", "Receivable aging 60-day amount is required.");
        this.bucket90Amount = requireNonNegative(bucket90Amount, "RECEIVABLE_AGING_BUCKET_90_REQUIRED", "Receivable aging 90-day amount is required.");
        this.bucketOver90Amount = requireNonNegative(bucketOver90Amount, "RECEIVABLE_AGING_BUCKET_OVER_90_REQUIRED", "Receivable aging over-90-day amount is required.");
        this.generatedAt = generatedAt;
    }

    private static String require(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String code, String message) {
        if (value == null) {
            throw new BusinessException(code, message);
        }
        BigDecimal normalized = value.setScale(2, RoundingMode.HALF_UP);
        if (normalized.signum() < 0) {
            throw new BusinessException("RECEIVABLE_AGING_AMOUNT_NEGATIVE", "Receivable aging amount cannot be negative.");
        }
        return normalized;
    }

    public String getPeriod() {
        return period;
    }

    public BigDecimal getCurrentAmount() {
        return currentAmount;
    }

    public BigDecimal getBucket30Amount() {
        return bucket30Amount;
    }

    public BigDecimal getBucket60Amount() {
        return bucket60Amount;
    }

    public BigDecimal getBucket90Amount() {
        return bucket90Amount;
    }

    public BigDecimal getBucketOver90Amount() {
        return bucketOver90Amount;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }
}
