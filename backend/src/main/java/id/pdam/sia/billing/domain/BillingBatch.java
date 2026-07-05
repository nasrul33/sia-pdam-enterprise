package id.pdam.sia.billing.domain;

import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "billing_batches")
public class BillingBatch extends BaseEntity {
    @Column(nullable = false, unique = true, length = 64)
    private String batchNumber;

    @Column(nullable = false, length = 7)
    private String period;

    @Column(nullable = false, length = 64)
    private String areaCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BillingBatchStatus status;

    @Column(nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    protected BillingBatch() {
    }

    public BillingBatch(String batchNumber, String period, String areaCode, String idempotencyKey) {
        this.batchNumber = require(batchNumber, "BILLING_BATCH_NUMBER_REQUIRED", "Billing batch number is required.");
        this.period = require(period, "BILLING_BATCH_PERIOD_REQUIRED", "Billing batch period is required.");
        this.areaCode = require(areaCode, "BILLING_BATCH_AREA_REQUIRED", "Billing batch area code is required.");
        this.idempotencyKey = require(idempotencyKey, "BILLING_BATCH_IDEMPOTENCY_REQUIRED", "Billing batch idempotency key is required.");
        this.status = BillingBatchStatus.RUNNING;
    }

    public void markCompleted() {
        if (status == BillingBatchStatus.VOID) {
            throw new BusinessException("BILLING_BATCH_VOID", "Void billing batch cannot be completed.");
        }
        status = BillingBatchStatus.COMPLETED;
    }

    public void markFailed() {
        if (status != BillingBatchStatus.COMPLETED && status != BillingBatchStatus.VOID) {
            status = BillingBatchStatus.FAILED;
        }
    }

    private static String require(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    public String getBatchNumber() {
        return batchNumber;
    }

    public String getPeriod() {
        return period;
    }

    public String getAreaCode() {
        return areaCode;
    }

    public BillingBatchStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
