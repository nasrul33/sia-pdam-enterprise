package id.pdam.sia.payment.repository;

import id.pdam.sia.payment.domain.PaymentWebhookEvent;
import id.pdam.sia.payment.domain.PaymentWebhookStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, UUID> {
    Optional<PaymentWebhookEvent> findByProviderAndExternalReference(String provider, String externalReference);

    Page<PaymentWebhookEvent> findByProvider(String provider, Pageable pageable);

    Page<PaymentWebhookEvent> findByStatus(PaymentWebhookStatus status, Pageable pageable);

    Page<PaymentWebhookEvent> findByProviderAndStatus(String provider, PaymentWebhookStatus status, Pageable pageable);
}
