package id.pdam.sia.billing.application;

import id.pdam.sia.shared.money.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TariffCalculationResult(
        UUID tariffVersionId,
        UUID tariffGroupId,
        LocalDate effectiveDate,
        LocalDate billingDate,
        BigDecimal usageM3,
        List<TariffCalculationLine> lines,
        Money usageCharge,
        Money fixedCharge,
        Money levyCharge,
        Money adminCharge,
        Money wasteCharge,
        Money penaltyCharge,
        Money subtotal,
        Money total
) {
    public TariffCalculationResult(
            UUID tariffVersionId,
            UUID tariffGroupId,
            LocalDate effectiveDate,
            LocalDate billingDate,
            BigDecimal usageM3,
            List<TariffCalculationLine> lines,
            Money subtotal
    ) {
        this(tariffVersionId, tariffGroupId, effectiveDate, billingDate, usageM3, lines,
                subtotal, Money.zero(), Money.zero(), Money.zero(), Money.zero(), Money.zero(), subtotal, subtotal);
    }
}
