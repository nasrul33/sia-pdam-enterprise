package id.pdam.sia.metering.web;

import id.pdam.sia.metering.domain.MeterRoute;

import java.time.Instant;
import java.util.UUID;

public record MeterRouteResponse(
        UUID id,
        String routeCode,
        String name,
        String areaCode,
        Instant createdAt,
        Instant updatedAt
) {
    public static MeterRouteResponse from(MeterRoute route) {
        return new MeterRouteResponse(
                route.getId(),
                route.getRouteCode(),
                route.getName(),
                route.getAreaCode(),
                route.getCreatedAt(),
                route.getUpdatedAt()
        );
    }
}
