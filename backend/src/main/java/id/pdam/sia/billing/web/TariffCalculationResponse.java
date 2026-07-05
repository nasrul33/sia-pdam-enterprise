package id.pdam.sia.billing.web;

import id.pdam.sia.billing.application.TariffCalculationResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TariffCalculationResponse(
        UUID tariffVersionId,
        UUID tariffGroupId,
        LocalDate effectiveDate,
        LocalDate billingDate,
        BigDecimal usageM3,
        List<TariffCalculationLineResponse> lines,
        BigDecimal subtotal
) {
    public static TariffCalculationResponse from(TariffCalculationResult result) {
        return new TariffCalculationResponse(
                result.tariffVersionId(),
                result.tariffGroupId(),
                result.effectiveDate(),
                result.billingDate(),
                result.usageM3(),
                result.lines().stream().map(TariffCalculationLineResponse::from).toList(),
                result.subtotal().amount()
        );
    }
}
