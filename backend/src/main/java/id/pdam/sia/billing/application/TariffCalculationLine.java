package id.pdam.sia.billing.application;

import id.pdam.sia.shared.money.Money;

import java.math.BigDecimal;

public record TariffCalculationLine(
        int blockOrder,
        BigDecimal minM3,
        BigDecimal maxM3,
        BigDecimal quantityM3,
        BigDecimal pricePerM3,
        Money amount
) {
}
