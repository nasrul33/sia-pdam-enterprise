package id.pdam.sia.billing.domain;

import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tariff_versions")
public class TariffVersion extends BaseEntity {
    @Column(nullable = false)
    private UUID tariffGroupId;

    @Column(nullable = false)
    private LocalDate effectiveDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TariffVersionStatus status;

    protected TariffVersion() {
    }

    public TariffVersion(UUID tariffGroupId, LocalDate effectiveDate) {
        if (tariffGroupId == null) {
            throw new BusinessException("TARIFF_GROUP_REQUIRED", "Tariff group is required.");
        }
        if (effectiveDate == null) {
            throw new BusinessException("TARIFF_EFFECTIVE_DATE_REQUIRED", "Tariff effective date is required.");
        }
        this.tariffGroupId = tariffGroupId;
        this.effectiveDate = effectiveDate;
        this.status = TariffVersionStatus.DRAFT;
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
}
