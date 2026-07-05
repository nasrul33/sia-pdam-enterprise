package id.pdam.sia.payment.repository;

import id.pdam.sia.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByExternalReference(String externalReference);
}
