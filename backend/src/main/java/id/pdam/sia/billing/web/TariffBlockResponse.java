package id.pdam.sia.billing.web;

import id.pdam.sia.billing.domain.TariffBlock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TariffBlockResponse(
        UUID id,
        UUID tariffVersionId,
        int blockOrder,
        BigDecimal minM3,
        BigDecimal maxM3,
        BigDecimal pricePerM3,
        Instant createdAt,
        Instant updatedAt
) {
    public static TariffBlockResponse from(TariffBlock block) {
        return new TariffBlockResponse(
                block.getId(),
                block.getTariffVersionId(),
                block.getBlockOrder(),
                block.getMinM3(),
                block.getMaxM3(),
                block.getPricePerM3(),
                block.getCreatedAt(),
                block.getUpdatedAt()
        );
    }
}
