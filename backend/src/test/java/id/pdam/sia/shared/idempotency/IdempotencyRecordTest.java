package id.pdam.sia.shared.idempotency;

import id.pdam.sia.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyRecordTest {
    @Test
    void reservesPendingKeyForPaymentWorkflow() {
        IdempotencyRecord record = IdempotencyRecord.reserve(
                "pay-202607-0001",
                "PAYMENT",
                "sha256:payload",
                Instant.now().plusSeconds(3600)
        );

        assertThat(record.getIdempotencyKey()).isEqualTo("pay-202607-0001");
        assertThat(record.getStatus()).isEqualTo(IdempotencyStatus.PENDING);
    }

    @Test
    void rejectsSameKeyWithDifferentPayload() {
        IdempotencyRecord record = IdempotencyRecord.reserve(
                "pay-202607-0001",
                "PAYMENT",
                "sha256:payload-a",
                Instant.now().plusSeconds(3600)
        );

        assertThatThrownBy(() -> record.ensureSameRequest("sha256:payload-b"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already used for a different payload");
    }

    @Test
    void marksCompletedOnceSettlementSucceeds() {
        IdempotencyRecord record = IdempotencyRecord.reserve(
                "pay-202607-0001",
                "PAYMENT",
                "sha256:payload",
                Instant.now().plusSeconds(3600)
        );

        record.markCompleted("PAY-2026-07-0001");

        assertThat(record.isCompleted()).isTrue();
        assertThat(record.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
        assertThat(record.getResponseReference()).isEqualTo("PAY-2026-07-0001");
    }
}
