package id.pdam.sia.payment.repository;

import id.pdam.sia.payment.domain.PaymentReconciliationItem;
import id.pdam.sia.payment.domain.PaymentReconciliationResolutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentReconciliationItemRepository extends JpaRepository<PaymentReconciliationItem, UUID> {
    List<PaymentReconciliationItem> findBySessionIdOrderByRowNumberAsc(UUID sessionId);

    List<PaymentReconciliationItem> findBySessionIdInOrderBySessionIdAscRowNumberAsc(Collection<UUID> sessionIds);

    Optional<PaymentReconciliationItem> findBySessionIdAndId(UUID sessionId, UUID id);

    long countBySessionIdAndResolutionStatus(UUID sessionId, PaymentReconciliationResolutionStatus resolutionStatus);
}
