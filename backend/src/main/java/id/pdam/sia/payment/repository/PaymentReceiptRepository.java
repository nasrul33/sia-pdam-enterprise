package id.pdam.sia.payment.repository;

import id.pdam.sia.payment.domain.PaymentReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentReceiptRepository extends JpaRepository<PaymentReceipt, UUID> {
    Optional<PaymentReceipt> findByPaymentId(UUID paymentId);
}
