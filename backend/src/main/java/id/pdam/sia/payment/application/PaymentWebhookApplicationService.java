package id.pdam.sia.payment.application;

import id.pdam.sia.payment.domain.PaymentWebhookEvent;
import id.pdam.sia.payment.domain.PaymentWebhookStatus;
import id.pdam.sia.payment.repository.PaymentWebhookEventRepository;
import id.pdam.sia.payment.web.PaymentWebhookRequest;
import id.pdam.sia.shared.audit.AuditTrailService;
import id.pdam.sia.shared.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentWebhookApplicationService {
    private static final int MAX_PAGE_SIZE = 100;

    private final PaymentWebhookEventRepository paymentWebhookEventRepository;
    private final AuditTrailService auditTrailService;
    private final String webhookSecret;

    public PaymentWebhookApplicationService(
            PaymentWebhookEventRepository paymentWebhookEventRepository,
            AuditTrailService auditTrailService,
            @Value("${sia.payment.webhook.secret:dev-only-change-me}") String webhookSecret
    ) {
        this.paymentWebhookEventRepository = paymentWebhookEventRepository;
        this.auditTrailService = auditTrailService;
        this.webhookSecret = webhookSecret;
    }

    @Transactional(readOnly = true)
    public Page<PaymentWebhookEvent> listEvents(String provider, PaymentWebhookStatus status, int page, int size) {
        Pageable pageable = pageable(page, size, Sort.by("receivedAt").descending());
        String normalizedProvider = normalizeOptional(provider);
        if (normalizedProvider != null && status != null) {
            return paymentWebhookEventRepository.findByProviderAndStatus(normalizedProvider, status, pageable);
        }
        if (normalizedProvider != null) {
            return paymentWebhookEventRepository.findByProvider(normalizedProvider, pageable);
        }
        if (status != null) {
            return paymentWebhookEventRepository.findByStatus(status, pageable);
        }
        return paymentWebhookEventRepository.findAll(pageable);
    }

    @Transactional
    public PaymentWebhookEvent receiveWebhook(PaymentWebhookRequest request, String signature, String actor) {
        if (!PaymentWebhookSignature.verify(webhookSecret, request, signature)) {
            throw new BusinessException("PAYMENT_WEBHOOK_SIGNATURE_INVALID", "Invalid payment webhook signature.");
        }

        PaymentWebhookEvent candidate = new PaymentWebhookEvent(
                request.provider(),
                request.externalReference(),
                request.idempotencyKey(),
                request.payload()
        );

        return paymentWebhookEventRepository
                .findByProviderAndExternalReference(candidate.getProvider(), candidate.getExternalReference())
                .orElseGet(() -> persistReceivedEvent(candidate, actor));
    }

    private PaymentWebhookEvent persistReceivedEvent(PaymentWebhookEvent event, String actor) {
        PaymentWebhookEvent saved = paymentWebhookEventRepository.save(event);
        auditTrailService.record(
                actor,
                "PAYMENT",
                "RECEIVE_PAYMENT_WEBHOOK",
                saved.getId().toString(),
                "signature validated"
        );
        return saved;
    }

    private static Pageable pageable(int page, int size, Sort sort) {
        if (page < 0) {
            throw new BusinessException("PAGE_INVALID", "Page must be zero or greater.");
        }
        if (size < 1) {
            throw new BusinessException("PAGE_SIZE_INVALID", "Page size must be at least one.");
        }
        return PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), sort);
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
