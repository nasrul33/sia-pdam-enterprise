package id.pdam.sia.accounting.repository;

import id.pdam.sia.accounting.domain.Supplier;
import id.pdam.sia.accounting.domain.SupplierStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
    Optional<Supplier> findByCode(String code);

    Page<Supplier> findByStatus(SupplierStatus status, Pageable pageable);
}
