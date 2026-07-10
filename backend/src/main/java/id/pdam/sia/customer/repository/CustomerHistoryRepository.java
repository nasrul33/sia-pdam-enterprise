package id.pdam.sia.customer.repository;

import id.pdam.sia.customer.domain.CustomerHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CustomerHistoryRepository extends JpaRepository<CustomerHistory, UUID> {
    List<CustomerHistory> findByCustomerIdOrderByChangedAtDesc(UUID customerId);
}
