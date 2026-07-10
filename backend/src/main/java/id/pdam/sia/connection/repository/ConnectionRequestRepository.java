package id.pdam.sia.connection.repository;

import id.pdam.sia.connection.domain.ConnectionRequest;
import id.pdam.sia.connection.domain.ConnectionRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConnectionRequestRepository extends JpaRepository<ConnectionRequest, UUID> {
    Optional<ConnectionRequest> findByRequestNumber(String requestNumber);

    Page<ConnectionRequest> findByStatus(ConnectionRequestStatus status, Pageable pageable);
}
