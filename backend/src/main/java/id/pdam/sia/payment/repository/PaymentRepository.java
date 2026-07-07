package id.pdam.sia.payment.repository;

import id.pdam.sia.payment.domain.Payment;
import id.pdam.sia.payment.domain.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {
    Optional<Payment> findByExternalReference(String externalReference);

    Optional<Payment> findByPaymentNumber(String paymentNumber);

    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);

    Page<Payment> findByChannel(String channel, Pageable pageable);

    Page<Payment> findByStatusAndChannel(PaymentStatus status, String channel, Pageable pageable);
}
