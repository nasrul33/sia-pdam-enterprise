package id.pdam.sia.payment.repository;

import id.pdam.sia.payment.domain.PaymentReconciliationSession;
import id.pdam.sia.payment.domain.PaymentReconciliationSessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface PaymentReconciliationSessionRepository extends JpaRepository<PaymentReconciliationSession, UUID>,
        JpaSpecificationExecutor<PaymentReconciliationSession> {
    Page<PaymentReconciliationSession> findByStatus(PaymentReconciliationSessionStatus status, Pageable pageable);

    @Query("""
            select count(session)
            from PaymentReconciliationSession session
            where session.startedAt >= :periodStart
              and session.startedAt < :periodEnd
              and session.status <> :cancelledStatus
              and (
                  session.status <> :completedStatus
                  or session.signedOffAt is null
              )
            """)
    long countUnfinishedForPeriod(
            @Param("periodStart") Instant periodStart,
            @Param("periodEnd") Instant periodEnd,
            @Param("cancelledStatus") PaymentReconciliationSessionStatus cancelledStatus,
            @Param("completedStatus") PaymentReconciliationSessionStatus completedStatus
    );
}
