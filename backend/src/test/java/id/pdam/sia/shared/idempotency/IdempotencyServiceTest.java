package id.pdam.sia.shared.idempotency;

import id.pdam.sia.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotencyServiceTest {
    private final IdempotencyRepository idempotencyRepository = mock(IdempotencyRepository.class);
    private final IdempotencyService idempotencyService = new IdempotencyService(idempotencyRepository);

    @Test
    void returnsExistingRecordWhenPayloadMatches() {
        IdempotencyRecord existing = IdempotencyRecord.reserve(
                "counter-payment-1",
                "PAYMENT",
                "sha256:same",
                Instant.now().plusSeconds(3600)
        );
        when(idempotencyRepository.findByIdempotencyKey("counter-payment-1")).thenReturn(Optional.of(existing));

        IdempotencyRecord result = idempotencyService.reserve(
                "counter-payment-1",
                "PAYMENT",
                "sha256:same",
                Instant.now().plusSeconds(3600)
        );

        assertThat(result).isSameAs(existing);
        verify(idempotencyRepository, never()).save(existing);
    }

    @Test
    void rejectsExistingRecordWhenPayloadDiffers() {
        IdempotencyRecord existing = IdempotencyRecord.reserve(
                "counter-payment-1",
                "PAYMENT",
                "sha256:a",
                Instant.now().plusSeconds(3600)
        );
        when(idempotencyRepository.findByIdempotencyKey("counter-payment-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> idempotencyService.reserve(
                "counter-payment-1",
                "PAYMENT",
                "sha256:b",
                Instant.now().plusSeconds(3600)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already used for a different payload");
    }
}
