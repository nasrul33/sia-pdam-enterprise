package id.pdam.sia.payment.application;

import id.pdam.sia.payment.domain.PaymentWebhookEvent;
import id.pdam.sia.payment.domain.PaymentWebhookStatus;
import id.pdam.sia.payment.repository.PaymentWebhookEventRepository;
import id.pdam.sia.payment.web.PaymentWebhookRequest;
import id.pdam.sia.shared.audit.AuditTrailService;
import id.pdam.sia.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentWebhookApplicationServiceTest {
    private static final String SECRET = "payment-webhook-secret";

    private final PaymentWebhookEventRepository paymentWebhookEventRepository = mock(PaymentWebhookEventRepository.class);
    private final AuditTrailService auditTrailService = mock(AuditTrailService.class);
    private final PaymentWebhookApplicationService service = new PaymentWebhookApplicationService(
            paymentWebhookEventRepository,
            auditTrailService,
            SECRET
    );

    @Test
    void acceptsWebhookWhenSignatureValidAndPersistsReceivedEvent() {
        PaymentWebhookRequest request = request();
        String signature = PaymentWebhookSignature.sign(SECRET, request);

        when(paymentWebhookEventRepository.findByProviderAndExternalReference("BANK-VA", "EXT-0001"))
                .thenReturn(Optional.empty());
        when(paymentWebhookEventRepository.save(any(PaymentWebhookEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentWebhookEvent event = service.receiveWebhook(request, signature, "bank-webhook");

        assertThat(event.getProvider()).isEqualTo("BANK-VA");
        assertThat(event.getExternalReference()).isEqualTo("EXT-0001");
        assertThat(event.getIdempotencyKey()).isEqualTo("PAY-EXT-0001");
        assertThat(event.getStatus()).isEqualTo(PaymentWebhookStatus.RECEIVED);
        assertThat(event.getPayload()).contains("\"amount\":100000");
        verify(auditTrailService).record(
                "bank-webhook",
                "PAYMENT",
                "RECEIVE_PAYMENT_WEBHOOK",
                event.getId().toString(),
                "signature validated"
        );
    }

    @Test
    void rejectsWebhookWhenSignatureInvalid() {
        PaymentWebhookRequest request = request();

        assertThatThrownBy(() -> service.receiveWebhook(request, "sha256=invalid", "bank-webhook"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid payment webhook signature");

        verify(paymentWebhookEventRepository, never()).save(any());
        verify(auditTrailService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void keepsStableValidationErrorWhenSignaturePayloadIsInvalid() {
        PaymentWebhookRequest request = new PaymentWebhookRequest(
                " ",
                "EXT-0001",
                "PAY-EXT-0001",
                "{\"amount\":100000}"
        );

        assertThatThrownBy(() -> PaymentWebhookSignature.sign(SECRET, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Payment webhook provider is required");
    }

    @Test
    void returnsExistingEventWhenProviderReferenceAlreadyReceived() {
        PaymentWebhookRequest request = request();
        String signature = PaymentWebhookSignature.sign(SECRET, request);
        PaymentWebhookEvent existing = new PaymentWebhookEvent(
                "BANK-VA",
                "EXT-0001",
                "PAY-EXT-0001",
                "{\"amount\":100000}"
        );

        when(paymentWebhookEventRepository.findByProviderAndExternalReference("BANK-VA", "EXT-0001"))
                .thenReturn(Optional.of(existing));

        PaymentWebhookEvent event = service.receiveWebhook(request, signature, "bank-webhook");

        assertThat(event).isSameAs(existing);
        verify(paymentWebhookEventRepository, never()).save(any());
    }

    private static PaymentWebhookRequest request() {
        return new PaymentWebhookRequest(
                "BANK-VA",
                "EXT-0001",
                "PAY-EXT-0001",
                "{\"amount\":100000}"
        );
    }
}
