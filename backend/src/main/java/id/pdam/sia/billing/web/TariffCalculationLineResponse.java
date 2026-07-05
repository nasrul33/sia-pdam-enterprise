package id.pdam.sia.billing.web;

import id.pdam.sia.billing.application.TariffCalculationLine;

import java.math.BigDecimal;

public record TariffCalculationLineResponse(
        int blockOrder,
        BigDecimal minM3,
        BigDecimal maxM3,
        BigDecimal quantityM3,
        BigDecimal pricePerM3,
        BigDecimal amount
) {
    public static TariffCalculationLineResponse from(TariffCalculationLine line) {
        return new TariffCalculationLineResponse(
                line.blockOrder(),
                line.minM3(),
                line.maxM3(),
                line.quantityM3(),
                line.pricePerM3(),
                line.amount().amount()
        );
    }
}
