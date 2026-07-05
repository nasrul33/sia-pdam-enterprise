package id.pdam.sia.customer.application;

import id.pdam.sia.customer.domain.Customer;
import id.pdam.sia.customer.repository.CustomerRepository;
import id.pdam.sia.customer.web.CreateCustomerRequest;
import id.pdam.sia.shared.audit.AuditTrailService;
import id.pdam.sia.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerApplicationServiceTest {
    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final AuditTrailService auditTrailService = mock(AuditTrailService.class);
    private final CustomerApplicationService service = new CustomerApplicationService(customerRepository, auditTrailService);

    @Test
    void createsCustomerWithAddressAndAuditTrail() {
        when(customerRepository.findByCustomerNumber("C-0001")).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Customer customer = service.createCustomer(
                new CreateCustomerRequest(
                        " C-0001 ",
                        "Budi Santoso",
                        "1371000000000001",
                        "081234567890",
                        "Jl. Merdeka No. 10",
                        "AREA-01",
                        new BigDecimal("-0.9500000"),
                        new BigDecimal("100.3600000"),
                        "registrasi pelanggan"
                ),
                "hublang.admin"
        );

        assertThat(customer.getCustomerNumber()).isEqualTo("C-0001");
        assertThat(customer.getAddresses()).hasSize(1);
        verify(auditTrailService).record(
                "hublang.admin",
                "CUSTOMER",
                "CREATE_CUSTOMER",
                customer.getId().toString(),
                "registrasi pelanggan"
        );
    }

    @Test
    void rejectsDuplicateCustomerNumber() {
        when(customerRepository.findByCustomerNumber("C-0001"))
                .thenReturn(Optional.of(new Customer("C-0001", "Budi Santoso", null, null)));

        assertThatThrownBy(() -> service.createCustomer(
                new CreateCustomerRequest(
                        "C-0001",
                        "Budi Santoso",
                        null,
                        null,
                        "Jl. Merdeka No. 10",
                        "AREA-01",
                        null,
                        null,
                        "registrasi pelanggan"
                ),
                "hublang.admin"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Customer number already exists");
    }
}
