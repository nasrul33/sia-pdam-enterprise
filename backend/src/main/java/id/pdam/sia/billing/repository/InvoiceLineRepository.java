package id.pdam.sia.billing.repository;

import id.pdam.sia.billing.domain.InvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, UUID> {
    List<InvoiceLine> findByInvoiceId(UUID invoiceId);
}
