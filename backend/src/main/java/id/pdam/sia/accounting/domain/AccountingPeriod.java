package id.pdam.sia.accounting.domain;

import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "accounting_periods")
public class AccountingPeriod extends BaseEntity {
    @Column(nullable = false, unique = true, length = 7)
    private String period;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PeriodStatus status;

    protected AccountingPeriod() {
    }

    public AccountingPeriod(String period) {
        if (!period.matches("\\d{4}-\\d{2}")) {
            throw new BusinessException("PERIOD_INVALID", "Period must use YYYY-MM format.");
        }
        this.period = period;
        this.status = PeriodStatus.OPEN;
    }

    public boolean allowsPosting() {
        return status == PeriodStatus.OPEN || status == PeriodStatus.REOPENED;
    }

    public void lock() {
        if (status != PeriodStatus.CLOSING_REVIEW) {
            throw new BusinessException("PERIOD_LOCK_INVALID", "Only closing review period can be locked.");
        }
        status = PeriodStatus.LOCKED;
    }

    public void startClosingReview() {
        if (status != PeriodStatus.OPEN) {
            throw new BusinessException("PERIOD_REVIEW_INVALID", "Only open period can enter closing review.");
        }
        status = PeriodStatus.CLOSING_REVIEW;
    }

    public String getPeriod() {
        return period;
    }

    public PeriodStatus getStatus() {
        return status;
    }
}
