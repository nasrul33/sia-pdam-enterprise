package id.pdam.sia.billing.application;

import id.pdam.sia.billing.domain.Invoice;
import id.pdam.sia.billing.domain.InvoiceLine;
import id.pdam.sia.billing.repository.InvoiceLineRepository;
import id.pdam.sia.billing.repository.InvoiceRepository;
import id.pdam.sia.connection.domain.Connection;
import id.pdam.sia.connection.repository.ConnectionRepository;
import id.pdam.sia.customer.domain.Customer;
import id.pdam.sia.customer.domain.CustomerAddress;
import id.pdam.sia.customer.repository.CustomerRepository;
import id.pdam.sia.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class InvoiceDocumentApplicationService {
    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final ConnectionRepository connectionRepository;
    private final CustomerRepository customerRepository;

    public InvoiceDocumentApplicationService(
            InvoiceRepository invoiceRepository,
            InvoiceLineRepository invoiceLineRepository,
            ConnectionRepository connectionRepository,
            CustomerRepository customerRepository
    ) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.connectionRepository = connectionRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public InvoiceDocument build(UUID invoiceId) {
        if (invoiceId == null) {
            throw new BusinessException("INVOICE_ID_REQUIRED", "Invoice id is required.");
        }
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new BusinessException("INVOICE_NOT_FOUND", "Invoice was not found."));
        Connection connection = connectionRepository.findById(invoice.getConnectionId())
                .orElseThrow(() -> new BusinessException("CONNECTION_NOT_FOUND", "Connection was not found."));
        Customer customer = customerRepository.findWithAddressesById(connection.getCustomerId())
                .orElseThrow(() -> new BusinessException("CUSTOMER_NOT_FOUND", "Customer was not found."));
        CustomerAddress primaryAddress = customer.getAddresses().stream()
                .min(Comparator.comparing(CustomerAddress::getAreaCode).thenComparing(CustomerAddress::getAddressLine))
                .orElse(null);
        List<InvoiceDocumentLine> lines = invoiceLineRepository.findByInvoiceId(invoice.getId()).stream()
                .map(InvoiceDocumentApplicationService::line)
                .toList();

        return new InvoiceDocument(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getPeriod(),
                invoice.getStatus(),
                new InvoiceDocumentCustomer(
                        customer.getId(),
                        customer.getCustomerNumber(),
                        customer.getFullName(),
                        primaryAddress == null ? null : primaryAddress.getAddressLine(),
                        primaryAddress == null ? null : primaryAddress.getAreaCode(),
                        customer.getPhoneNumber()
                ),
                new InvoiceDocumentConnection(
                        connection.getId(),
                        connection.getConnectionNumber(),
                        connection.getMeterNumber()
                ),
                lines,
                invoice.getSubtotal(),
                invoice.getPenaltyAmount(),
                invoice.getPaidAmount(),
                invoice.getOutstandingAmount(),
                invoice.getDueDate(),
                invoice.getIssuedAt(),
                invoice.getIssueJournalEntryId(),
                invoice.getVoidedAt(),
                invoice.getVoidJournalEntryId()
        );
    }

    private static InvoiceDocumentLine line(InvoiceLine line) {
        return new InvoiceDocumentLine(
                line.getLineType(),
                line.getDescription(),
                line.getQuantity(),
                line.getUnitPrice(),
                line.getAmount()
        );
    }
}
