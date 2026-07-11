package id.pdam.sia.billing.repository;

import id.pdam.sia.billing.domain.Invoice;
import id.pdam.sia.billing.domain.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    boolean existsByConnectionIdAndPeriod(UUID connectionId, String period);

    List<Invoice> findByBillingBatchId(UUID billingBatchId);

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    default List<Invoice> findOpenReceivables() {
        return findByStatusInAndOutstandingAmountGreaterThan(
                List.of(InvoiceStatus.ISSUED, InvoiceStatus.PARTIAL_PAID),
                BigDecimal.ZERO
        );
    }

    List<Invoice> findByStatusInAndOutstandingAmountGreaterThan(
            Collection<InvoiceStatus> statuses,
            BigDecimal outstandingAmount
    );

    Page<Invoice> findByPeriod(String period, Pageable pageable);

    Page<Invoice> findByStatus(InvoiceStatus status, Pageable pageable);

    Page<Invoice> findByPeriodAndStatus(String period, InvoiceStatus status, Pageable pageable);

    long countByPeriodAndStatus(String period, InvoiceStatus status);

    @Query("""
            select coalesce(sum(invoice.outstandingAmount), 0)
            from Invoice invoice
            where invoice.connectionId = :connectionId
              and invoice.status in :statuses
              and invoice.outstandingAmount > 0
            """)
    BigDecimal sumOutstandingByConnectionIdAndStatuses(
            @Param("connectionId") UUID connectionId,
            @Param("statuses") Collection<InvoiceStatus> statuses
    );
}
