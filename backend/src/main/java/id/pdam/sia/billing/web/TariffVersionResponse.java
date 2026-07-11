package id.pdam.sia.billing.web;

import id.pdam.sia.billing.domain.TariffVersion;
import id.pdam.sia.billing.domain.TariffVersionStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record TariffVersionResponse(
        UUID id,
        UUID tariffGroupId,
        LocalDate effectiveDate,
        BigDecimal fixedCharge,
        BigDecimal levyCharge,
        BigDecimal adminCharge,
        BigDecimal wasteCharge,
        BigDecimal penaltyRate,
        TariffVersionStatus status,
        List<String> availableActions,
        Instant createdAt,
        Instant updatedAt
) {
    public static TariffVersionResponse from(TariffVersion version) {
        return new TariffVersionResponse(
                version.getId(),
                version.getTariffGroupId(),
                version.getEffectiveDate(),
                version.getFixedCharge(),
                version.getLevyCharge(),
                version.getAdminCharge(),
                version.getWasteCharge(),
                version.getPenaltyRate(),
                version.getStatus(),
                availableActions(version),
                version.getCreatedAt(),
                version.getUpdatedAt()
        );
    }

    private static List<String> availableActions(TariffVersion version) {
        return switch (version.getStatus()) {
            case DRAFT -> List.of("add-block", "activate", "archive");
            case ACTIVE -> List.of("archive");
            case ARCHIVED -> List.of();
        };
    }
}
