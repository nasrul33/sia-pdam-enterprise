package id.pdam.sia.connection.repository;

import id.pdam.sia.connection.domain.Connection;
import id.pdam.sia.connection.domain.ConnectionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConnectionRepository extends JpaRepository<Connection, UUID> {
    Optional<Connection> findByConnectionNumber(String connectionNumber);

    Page<Connection> findByCustomerId(UUID customerId, Pageable pageable);

    Page<Connection> findByStatus(ConnectionStatus status, Pageable pageable);

    Page<Connection> findByCustomerIdAndStatus(UUID customerId, ConnectionStatus status, Pageable pageable);
}
