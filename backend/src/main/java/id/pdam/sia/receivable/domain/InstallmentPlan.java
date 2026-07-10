package id.pdam.sia.receivable.domain;

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
import java.util.UUID;

@Entity
@Table(name = "installment_plans")
public class InstallmentPlan extends BaseEntity {
    @Column(nullable = false)
    private UUID invoiceId;

    @Column(nullable = false, unique = true, length = 64)
    private String planNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InstallmentPlanStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private int installmentCount;

    @Column(nullable = false, length = 128)
    private String createdBy;

    @Column(length = 128)
    private String approvedBy;

    private Instant approvedAt;

    private Instant completedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    protected InstallmentPlan() {
    }

    public InstallmentPlan(UUID invoiceId, String planNumber, BigDecimal totalAmount, int installmentCount, String createdBy, String notes) {
        if (invoiceId == null) {
            throw new BusinessException("INSTALLMENT_INVOICE_REQUIRED", "Installment invoice is required.");
        }
        if (installmentCount < 1) {
            throw new BusinessException("INSTALLMENT_COUNT_INVALID", "Installment count must be greater than zero.");
        }
        this.invoiceId = invoiceId;
        this.planNumber = require(planNumber, "INSTALLMENT_PLAN_NUMBER_REQUIRED", "Installment plan number is required.");
        this.totalAmount = requirePositive(totalAmount);
        this.installmentCount = installmentCount;
        this.createdBy = require(createdBy, "INSTALLMENT_CREATED_BY_REQUIRED", "Installment actor is required.");
        this.notes = normalize(notes);
        this.status = InstallmentPlanStatus.ACTIVE;
    }

    public void approve(String actor) {
        if (status != InstallmentPlanStatus.ACTIVE) {
            throw new BusinessException("INSTALLMENT_APPROVE_STATUS_INVALID", "Only active installment plan can be approved.");
        }
        this.approvedBy = require(actor, "INSTALLMENT_APPROVED_BY_REQUIRED", "Installment approval actor is required.");
        this.approvedAt = Instant.now();
    }

    private static BigDecimal requirePositive(BigDecimal value) {
        BigDecimal normalized = (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
        if (normalized.signum() <= 0) {
            throw new BusinessException("INSTALLMENT_TOTAL_INVALID", "Installment total amount must be greater than zero.");
        }
        return normalized;
    }

    private static String require(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public String getPlanNumber() {
        return planNumber;
    }

    public InstallmentPlanStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public int getInstallmentCount() {
        return installmentCount;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getNotes() {
        return notes;
    }
}
