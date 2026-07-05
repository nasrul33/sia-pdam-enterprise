package id.pdam.sia.customer.repository;

import id.pdam.sia.customer.domain.Customer;
import id.pdam.sia.customer.domain.CustomerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByCustomerNumber(String customerNumber);

    Page<Customer> findByStatus(CustomerStatus status, Pageable pageable);

    Page<Customer> findByFullNameContainingIgnoreCase(String search, Pageable pageable);

    Page<Customer> findByStatusAndFullNameContainingIgnoreCase(CustomerStatus status, String search, Pageable pageable);

    @EntityGraph(attributePaths = "addresses")
    Optional<Customer> findWithAddressesById(UUID id);
}
