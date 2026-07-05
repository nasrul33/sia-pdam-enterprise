package id.pdam.sia.connection.web;

import id.pdam.sia.connection.domain.TariffGroup;

import java.time.Instant;
import java.util.UUID;

public record TariffGroupResponse(
        UUID id,
        String code,
        String name,
        Instant createdAt,
        Instant updatedAt
) {
    public static TariffGroupResponse from(TariffGroup tariffGroup) {
        return new TariffGroupResponse(
                tariffGroup.getId(),
                tariffGroup.getCode(),
                tariffGroup.getName(),
                tariffGroup.getCreatedAt(),
                tariffGroup.getUpdatedAt()
        );
    }
}
