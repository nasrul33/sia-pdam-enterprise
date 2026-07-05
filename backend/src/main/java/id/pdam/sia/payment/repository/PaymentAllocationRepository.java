package id.pdam.sia.payment.repository;

import id.pdam.sia.payment.domain.PaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, UUID> {
    List<PaymentAllocation> findByPaymentId(UUID paymentId);
}
