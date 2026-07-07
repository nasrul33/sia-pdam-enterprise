package id.pdam.sia.payment.repository;

import id.pdam.sia.payment.domain.Payment;
import id.pdam.sia.payment.domain.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByExternalReference(String externalReference);

    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);

    Page<Payment> findByChannel(String channel, Pageable pageable);

    Page<Payment> findByStatusAndChannel(PaymentStatus status, String channel, Pageable pageable);
}
