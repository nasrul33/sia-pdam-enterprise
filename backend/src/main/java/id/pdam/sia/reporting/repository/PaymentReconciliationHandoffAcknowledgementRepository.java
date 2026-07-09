package id.pdam.sia.reporting.repository;

import id.pdam.sia.reporting.domain.PaymentReconciliationHandoffAcknowledgement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentReconciliationHandoffAcknowledgementRepository
        extends JpaRepository<PaymentReconciliationHandoffAcknowledgement, UUID> {
    Optional<PaymentReconciliationHandoffAcknowledgement> findByPacketScopeHash(String packetScopeHash);
}
