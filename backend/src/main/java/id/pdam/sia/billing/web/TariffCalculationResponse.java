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
        BigDecimal usageCharge,
        BigDecimal fixedCharge,
        BigDecimal levyCharge,
        BigDecimal adminCharge,
        BigDecimal wasteCharge,
        BigDecimal penaltyCharge,
        BigDecimal subtotal,
        BigDecimal total
) {
    public static TariffCalculationResponse from(TariffCalculationResult result) {
        return new TariffCalculationResponse(
                result.tariffVersionId(),
                result.tariffGroupId(),
                result.effectiveDate(),
                result.billingDate(),
                result.usageM3(),
                result.lines().stream().map(TariffCalculationLineResponse::from).toList(),
                result.usageCharge().amount(),
                result.fixedCharge().amount(),
                result.levyCharge().amount(),
                result.adminCharge().amount(),
                result.wasteCharge().amount(),
                result.penaltyCharge().amount(),
                result.subtotal().amount(),
                result.total().amount()
        );
    }
}
