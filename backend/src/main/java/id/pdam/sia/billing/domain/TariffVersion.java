package id.pdam.sia.billing.domain;

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
@Table(name = "tariff_versions")
public class TariffVersion extends BaseEntity {
    @Column(nullable = false)
    private UUID tariffGroupId;

    @Column(nullable = false)
    private LocalDate effectiveDate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal fixedCharge;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal levyCharge;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal adminCharge;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal wasteCharge;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal penaltyRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TariffVersionStatus status;

    protected TariffVersion() {
    }

    public TariffVersion(UUID tariffGroupId, LocalDate effectiveDate) {
        this(tariffGroupId, effectiveDate, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public TariffVersion(
            UUID tariffGroupId,
            LocalDate effectiveDate,
            BigDecimal fixedCharge,
            BigDecimal levyCharge,
            BigDecimal adminCharge,
            BigDecimal wasteCharge,
            BigDecimal penaltyRate
    ) {
        if (tariffGroupId == null) {
            throw new BusinessException("TARIFF_GROUP_REQUIRED", "Tariff group is required.");
        }
        if (effectiveDate == null) {
            throw new BusinessException("TARIFF_EFFECTIVE_DATE_REQUIRED", "Tariff effective date is required.");
        }
        this.tariffGroupId = tariffGroupId;
        this.effectiveDate = effectiveDate;
        this.fixedCharge = nonNegative(fixedCharge, "TARIFF_FIXED_CHARGE_INVALID");
        this.levyCharge = nonNegative(levyCharge, "TARIFF_LEVY_CHARGE_INVALID");
        this.adminCharge = nonNegative(adminCharge, "TARIFF_ADMIN_CHARGE_INVALID");
        this.wasteCharge = nonNegative(wasteCharge, "TARIFF_WASTE_CHARGE_INVALID");
        this.penaltyRate = rate(penaltyRate);
        this.status = TariffVersionStatus.DRAFT;
    }

    private static BigDecimal nonNegative(BigDecimal value, String code) {
        BigDecimal normalized = value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP);
        if (normalized.signum() < 0) {
            throw new BusinessException(code, "Tariff charge cannot be negative.");
        }
        return normalized;
    }

    private static BigDecimal rate(BigDecimal value) {
        BigDecimal normalized = value == null ? BigDecimal.ZERO.setScale(6) : value.setScale(6, RoundingMode.HALF_UP);
        if (normalized.signum() < 0 || normalized.compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException("TARIFF_PENALTY_RATE_INVALID", "Penalty rate must be between zero and one.");
        }
        return normalized;
    }

    public void activate() {
        if (status != TariffVersionStatus.DRAFT) {
            throw new BusinessException("TARIFF_VERSION_ACTIVATE_INVALID", "Only draft tariff version can be activated.");
        }
        status = TariffVersionStatus.ACTIVE;
    }

    public void archive() {
        if (status == TariffVersionStatus.ARCHIVED) {
            return;
        }
        status = TariffVersionStatus.ARCHIVED;
    }

    public boolean isDraft() {
        return status == TariffVersionStatus.DRAFT;
    }

    public UUID getTariffGroupId() {
        return tariffGroupId;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public TariffVersionStatus getStatus() {
        return status;
    }

    public BigDecimal getFixedCharge() { return fixedCharge; }

    public BigDecimal getLevyCharge() { return levyCharge; }

    public BigDecimal getAdminCharge() { return adminCharge; }

    public BigDecimal getWasteCharge() { return wasteCharge; }

    public BigDecimal getPenaltyRate() { return penaltyRate; }
}
