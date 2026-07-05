package id.pdam.sia.billing.domain;

import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "tariff_blocks")
public class TariffBlock extends BaseEntity {
    @Column(nullable = false)
    private UUID tariffVersionId;

    @Column(nullable = false)
    private int blockOrder;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal minM3;

    @Column(precision = 14, scale = 3)
    private BigDecimal maxM3;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal pricePerM3;

    protected TariffBlock() {
    }

    public TariffBlock(
            UUID tariffVersionId,
            int blockOrder,
            BigDecimal minM3,
            BigDecimal maxM3,
            BigDecimal pricePerM3
    ) {
        if (tariffVersionId == null) {
            throw new BusinessException("TARIFF_VERSION_REQUIRED", "Tariff version is required.");
        }
        if (blockOrder < 1) {
            throw new BusinessException("TARIFF_BLOCK_ORDER_INVALID", "Tariff block order must be at least one.");
        }
        this.tariffVersionId = tariffVersionId;
        this.blockOrder = blockOrder;
        this.minM3 = requireNonNegative(minM3, "TARIFF_BLOCK_MIN_REQUIRED", "Tariff block minimum usage is required.");
        this.maxM3 = normalizeMax(maxM3, this.minM3);
        this.pricePerM3 = requireNonNegative(pricePerM3, "TARIFF_BLOCK_PRICE_REQUIRED", "Tariff block price is required.");
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String code, String message) {
        if (value == null) {
            throw new BusinessException(code, message);
        }
        if (value.signum() < 0) {
            throw new BusinessException("TARIFF_BLOCK_NEGATIVE", "Tariff block value cannot be negative.");
        }
        return value.stripTrailingZeros();
    }

    private static BigDecimal normalizeMax(BigDecimal maxM3, BigDecimal minM3) {
        if (maxM3 == null) {
            return null;
        }
        if (maxM3.compareTo(minM3) <= 0) {
            throw new BusinessException("TARIFF_BLOCK_RANGE_INVALID", "Tariff block maximum usage must be greater than minimum usage.");
        }
        return maxM3.stripTrailingZeros();
    }

    public UUID getTariffVersionId() {
        return tariffVersionId;
    }

    public int getBlockOrder() {
        return blockOrder;
    }

    public BigDecimal getMinM3() {
        return minM3;
    }

    public BigDecimal getMaxM3() {
        return maxM3;
    }

    public BigDecimal getPricePerM3() {
        return pricePerM3;
    }
}
