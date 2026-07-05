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
        Money subtotal
) {
}
