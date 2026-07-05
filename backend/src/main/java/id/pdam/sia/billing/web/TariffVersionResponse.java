package id.pdam.sia.billing.web;

import id.pdam.sia.billing.domain.TariffVersion;
import id.pdam.sia.billing.domain.TariffVersionStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TariffVersionResponse(
        UUID id,
        UUID tariffGroupId,
        LocalDate effectiveDate,
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
