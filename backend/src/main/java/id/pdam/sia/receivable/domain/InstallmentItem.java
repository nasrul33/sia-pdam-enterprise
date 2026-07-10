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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "installment_items")
public class InstallmentItem extends BaseEntity {
    @Column(nullable = false)
    private UUID planId;

    @Column(nullable = false)
    private int installmentNumber;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal paidAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InstallmentItemStatus status;

    protected InstallmentItem() {
    }

    public InstallmentItem(UUID planId, int installmentNumber, LocalDate dueDate, BigDecimal amount) {
        if (planId == null) {
            throw new BusinessException("INSTALLMENT_ITEM_PLAN_REQUIRED", "Installment item plan is required.");
        }
        if (installmentNumber < 1) {
            throw new BusinessException("INSTALLMENT_ITEM_NUMBER_INVALID", "Installment item number must be greater than zero.");
        }
        if (dueDate == null) {
            throw new BusinessException("INSTALLMENT_ITEM_DUE_DATE_REQUIRED", "Installment item due date is required.");
        }
        this.planId = planId;
        this.installmentNumber = installmentNumber;
        this.dueDate = dueDate;
        this.amount = requirePositive(amount);
        this.paidAmount = BigDecimal.ZERO.setScale(2);
        this.status = InstallmentItemStatus.OPEN;
    }

    private static BigDecimal requirePositive(BigDecimal value) {
        BigDecimal normalized = (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
        if (normalized.signum() <= 0) {
            throw new BusinessException("INSTALLMENT_ITEM_AMOUNT_INVALID", "Installment item amount must be greater than zero.");
        }
        return normalized;
    }

    public UUID getPlanId() {
        return planId;
    }

    public int getInstallmentNumber() {
        return installmentNumber;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public InstallmentItemStatus getStatus() {
        return status;
    }
}
