package id.pdam.sia.connection.web;

import id.pdam.sia.connection.domain.Connection;
import id.pdam.sia.connection.domain.ConnectionStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ConnectionResponse(
        UUID id,
        UUID customerId,
        UUID tariffGroupId,
        String connectionNumber,
        String meterNumber,
        ConnectionStatus status,
        LocalDate installedAt,
        List<String> availableActions,
        Instant createdAt,
        Instant updatedAt
) {
    public static ConnectionResponse from(Connection connection) {
        return new ConnectionResponse(
                connection.getId(),
                connection.getCustomerId(),
                connection.getTariffGroupId(),
                connection.getConnectionNumber(),
                connection.getMeterNumber(),
                connection.getStatus(),
                connection.getInstalledAt(),
                availableActions(connection),
                connection.getCreatedAt(),
                connection.getUpdatedAt()
        );
    }

    private static List<String> availableActions(Connection connection) {
        return switch (connection.getStatus()) {
            case DRAFT -> List.of("ACTIVATE");
            case ACTIVE -> List.of("SUSPEND", "TERMINATE");
            case SUSPENDED -> List.of("ACTIVATE", "TERMINATE");
            case TERMINATED -> List.of();
        };
    }
}
