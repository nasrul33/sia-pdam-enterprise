package id.pdam.sia.billing.application;

import id.pdam.sia.billing.domain.Invoice;
import id.pdam.sia.billing.domain.InvoiceLine;
import id.pdam.sia.billing.domain.InvoiceLineType;
import id.pdam.sia.billing.repository.InvoiceLineRepository;
import id.pdam.sia.billing.repository.InvoiceRepository;
import id.pdam.sia.connection.domain.Connection;
import id.pdam.sia.connection.repository.ConnectionRepository;
import id.pdam.sia.customer.domain.Customer;
import id.pdam.sia.customer.repository.CustomerRepository;
import id.pdam.sia.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InvoiceDocumentApplicationServiceTest {
    private final InvoiceRepository invoiceRepository = mock(InvoiceRepository.class);
    private final InvoiceLineRepository invoiceLineRepository = mock(InvoiceLineRepository.class);
    private final ConnectionRepository connectionRepository = mock(ConnectionRepository.class);
    private final CustomerRepository customerRepository = mock(CustomerRepository.class);

    private final InvoiceDocumentApplicationService service = new InvoiceDocumentApplicationService(
            invoiceRepository,
            invoiceLineRepository,
            connectionRepository,
            customerRepository
    );

    @Test
    void buildsInvoiceDocumentFromInvoiceConnectionCustomerAndLines() {
        Customer customer = new Customer("CUST-0001", "Pelanggan Uji", "3171", "08123456789");
        customer.addAddress("Jalan Uji No. 1", "AREA-01", null, null);
        Connection connection = new Connection(
                customer.getId(),
                UUID.randomUUID(),
                "SR-0001",
                "MTR-0001",
                LocalDate.of(2026, 1, 15)
        );
        Invoice invoice = new Invoice(
                UUID.randomUUID(),
                connection.getId(),
                "INV-202607-SR-0001",
                "2026-07",
                new BigDecimal("46250.00"),
                new BigDecimal("5000.00"),
                new BigDecimal("2000.00"),
                new BigDecimal("2500.00"),
                new BigDecimal("3000.00"),
                new BigDecimal("1500.00"),
                LocalDate.of(2026, 8, 20)
        );
        UUID issueJournalId = UUID.randomUUID();
        invoice.markIssued(Instant.parse("2026-07-31T12:00:00Z"), issueJournalId);
        InvoiceLine line = new InvoiceLine(
                invoice.getId(),
                InvoiceLineType.WATER_USAGE,
                "Pemakaian air blok 1",
                new BigDecimal("18.500"),
                new BigDecimal("2500.00"),
                new BigDecimal("46250.00")
        );

        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(connectionRepository.findById(connection.getId())).thenReturn(Optional.of(connection));
        when(customerRepository.findWithAddressesById(customer.getId())).thenReturn(Optional.of(customer));
        when(invoiceLineRepository.findByInvoiceId(invoice.getId())).thenReturn(List.of(line));

        InvoiceDocument document = service.build(invoice.getId());

        assertThat(document.invoiceNumber()).isEqualTo("INV-202607-SR-0001");
        assertThat(document.customer().customerNumber()).isEqualTo("CUST-0001");
        assertThat(document.customer().addressLine()).isEqualTo("Jalan Uji No. 1");
        assertThat(document.connection().connectionNumber()).isEqualTo("SR-0001");
        assertThat(document.lines()).hasSize(1);
        assertThat(document.lines().getFirst().amount()).isEqualByComparingTo("46250.00");
        assertThat(document.usageCharge()).isEqualByComparingTo("46250.00");
        assertThat(document.fixedCharge()).isEqualByComparingTo("5000.00");
        assertThat(document.levyCharge()).isEqualByComparingTo("2000.00");
        assertThat(document.adminCharge()).isEqualByComparingTo("2500.00");
        assertThat(document.wasteCharge()).isEqualByComparingTo("3000.00");
        assertThat(document.subtotal()).isEqualByComparingTo("58750.00");
        assertThat(document.penaltyAmount()).isEqualByComparingTo("1500.00");
        assertThat(document.outstandingAmount()).isEqualByComparingTo("60250.00");
        assertThat(document.issueJournalEntryId()).isEqualTo(issueJournalId);
    }

    @Test
    void rejectsMissingInvoice() {
        UUID invoiceId = UUID.randomUUID();
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.build(invoiceId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invoice was not found");
    }
}
