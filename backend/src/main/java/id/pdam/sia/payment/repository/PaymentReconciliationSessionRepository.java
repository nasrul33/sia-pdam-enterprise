package id.pdam.sia.payment.repository;

import id.pdam.sia.payment.domain.PaymentReconciliationSession;
import id.pdam.sia.payment.domain.PaymentReconciliationSessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentReconciliationSessionRepository extends JpaRepository<PaymentReconciliationSession, UUID> {
    Page<PaymentReconciliationSession> findByStatus(PaymentReconciliationSessionStatus status, Pageable pageable);
}
