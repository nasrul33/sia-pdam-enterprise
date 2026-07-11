package id.pdam.sia.customer.repository;

import id.pdam.sia.customer.domain.Customer;
import id.pdam.sia.customer.domain.CustomerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByCustomerNumber(String customerNumber);

    Page<Customer> findByStatus(CustomerStatus status, Pageable pageable);

    @Query("""
            select customer from Customer customer
            where (:status is null or customer.status = :status)
              and (:search is null
                   or lower(customer.customerNumber) like lower(concat('%', :search, '%'))
                   or lower(customer.fullName) like lower(concat('%', :search, '%')))
            """)
    Page<Customer> search(
            @Param("status") CustomerStatus status,
            @Param("search") String search,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "addresses")
    Optional<Customer> findWithAddressesById(UUID id);
}
