package id.pdam.sia.connection.repository;

import id.pdam.sia.connection.domain.Connection;
import id.pdam.sia.connection.domain.ConnectionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ConnectionRepository extends JpaRepository<Connection, UUID> {
    Optional<Connection> findByConnectionNumber(String connectionNumber);

    Page<Connection> findByCustomerId(UUID customerId, Pageable pageable);

    Page<Connection> findByStatus(ConnectionStatus status, Pageable pageable);

    Page<Connection> findByCustomerIdAndStatus(UUID customerId, ConnectionStatus status, Pageable pageable);

    @Query("""
            select connection from Connection connection
            where (:customerId is null or connection.customerId = :customerId)
              and (:status is null or connection.status = :status)
              and (:search is null
                   or lower(connection.connectionNumber) like lower(concat('%', :search, '%'))
                   or lower(connection.meterNumber) like lower(concat('%', :search, '%')))
            """)
    Page<Connection> search(
            @Param("customerId") UUID customerId,
            @Param("status") ConnectionStatus status,
            @Param("search") String search,
            Pageable pageable
    );
}
