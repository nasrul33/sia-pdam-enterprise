package id.pdam.sia.receivable.web;

import id.pdam.sia.receivable.domain.CollectionActionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record CreateCollectionActionRequest(
        @NotNull UUID customerId,
        UUID invoiceId,
        @NotNull CollectionActionType actionType,
        @NotNull Instant scheduledAt,
        @Size(max = 1000) String notes,
        @NotBlank @Size(max = 500) String reason
) {
}
