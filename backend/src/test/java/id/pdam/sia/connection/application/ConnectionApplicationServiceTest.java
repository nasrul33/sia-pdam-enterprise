package id.pdam.sia.connection.application;

import id.pdam.sia.connection.domain.Connection;
import id.pdam.sia.connection.domain.ConnectionStatus;
import id.pdam.sia.connection.domain.TariffGroup;
import id.pdam.sia.connection.repository.ConnectionRepository;
import id.pdam.sia.connection.repository.TariffGroupRepository;
import id.pdam.sia.connection.web.ConnectionWorkflowRequest;
import id.pdam.sia.connection.web.CreateConnectionRequest;
import id.pdam.sia.connection.web.CreateTariffGroupRequest;
import id.pdam.sia.customer.domain.Customer;
import id.pdam.sia.customer.repository.CustomerRepository;
import id.pdam.sia.shared.audit.AuditTrailService;
import id.pdam.sia.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectionApplicationServiceTest {
    private final ConnectionRepository connectionRepository = mock(ConnectionRepository.class);
    private final TariffGroupRepository tariffGroupRepository = mock(TariffGroupRepository.class);
    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final AuditTrailService auditTrailService = mock(AuditTrailService.class);

    private final ConnectionApplicationService service = new ConnectionApplicationService(
            connectionRepository,
            tariffGroupRepository,
            customerRepository,
            auditTrailService
    );

    @Test
    void createsTariffGroupAndAuditTrail() {
        when(tariffGroupRepository.findByCode("R1")).thenReturn(Optional.empty());
        when(tariffGroupRepository.save(any(TariffGroup.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TariffGroup tariffGroup = service.createTariffGroup(
                new CreateTariffGroupRequest(" R1 ", "Rumah Tangga 1", "setup tarif"),
                "hublang.admin"
        );

        assertThat(tariffGroup.getCode()).isEqualTo("R1");
        verify(auditTrailService).record(
                "hublang.admin",
                "CONNECTION",
                "CREATE_TARIFF_GROUP",
                tariffGroup.getId().toString(),
                "setup tarif"
        );
    }

    @Test
    void createsDraftConnectionForActiveCustomer() {
        Customer customer = new Customer("C-0001", "Budi Santoso", null, null);
        UUID tariffGroupId = UUID.randomUUID();

        when(connectionRepository.findByConnectionNumber("SR-0001")).thenReturn(Optional.empty());
        when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(tariffGroupRepository.existsById(tariffGroupId)).thenReturn(true);
        when(connectionRepository.save(any(Connection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Connection connection = service.createConnection(
                new CreateConnectionRequest(
                        customer.getId(),
                        tariffGroupId,
                        " SR-0001 ",
                        "MTR-0001",
                        LocalDate.of(2026, 7, 5),
                        "pasang baru"
                ),
                "hublang.admin"
        );

        assertThat(connection.getConnectionNumber()).isEqualTo("SR-0001");
        assertThat(connection.getStatus()).isEqualTo(ConnectionStatus.DRAFT);
        verify(auditTrailService).record(
                "hublang.admin",
                "CONNECTION",
                "CREATE_CONNECTION",
                connection.getId().toString(),
                "pasang baru"
        );
    }

    @Test
    void blocksConnectionForInactiveCustomer() {
        Customer customer = new Customer("C-0001", "Budi Santoso", null, null);
        customer.deactivate();
        UUID tariffGroupId = UUID.randomUUID();

        when(connectionRepository.findByConnectionNumber("SR-0001")).thenReturn(Optional.empty());
        when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> service.createConnection(
                new CreateConnectionRequest(
                        customer.getId(),
                        tariffGroupId,
                        "SR-0001",
                        "MTR-0001",
                        LocalDate.of(2026, 7, 5),
                        "pasang baru"
                ),
                "hublang.admin"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("active customer");
    }

    @Test
    void blocksConnectionWhenTariffGroupMissing() {
        Customer customer = new Customer("C-0001", "Budi Santoso", null, null);
        UUID tariffGroupId = UUID.randomUUID();

        when(connectionRepository.findByConnectionNumber("SR-0001")).thenReturn(Optional.empty());
        when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(tariffGroupRepository.existsById(tariffGroupId)).thenReturn(false);

        assertThatThrownBy(() -> service.createConnection(
                new CreateConnectionRequest(
                        customer.getId(),
                        tariffGroupId,
                        "SR-0001",
                        "MTR-0001",
                        LocalDate.of(2026, 7, 5),
                        "pasang baru"
                ),
                "hublang.admin"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Tariff group was not found");
    }

    @Test
    void runsConnectionLifecycleWithAuditTrail() {
        Customer customer = new Customer("C-0001", "Budi Santoso", null, null);
        Connection connection = new Connection(
                customer.getId(),
                UUID.randomUUID(),
                "SR-0001",
                "MTR-0001",
                LocalDate.of(2026, 7, 5)
        );

        when(connectionRepository.findById(connection.getId())).thenReturn(Optional.of(connection));
        when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));

        service.activateConnection(connection.getId(), "aktifkan sambungan", "hublang.spv");
        service.suspendConnection(connection.getId(), "tunggakan", "hublang.spv");
        service.terminateConnection(connection.getId(), "berhenti langganan", "hublang.spv");

        assertThat(connection.getStatus()).isEqualTo(ConnectionStatus.TERMINATED);
        verify(auditTrailService).record(
                "hublang.spv",
                "CONNECTION",
                "ACTIVATE_CONNECTION",
                connection.getId().toString(),
                "aktifkan sambungan"
        );
        verify(auditTrailService).record(
                "hublang.spv",
                "CONNECTION",
                "SUSPEND_CONNECTION",
                connection.getId().toString(),
                "tunggakan"
        );
        verify(auditTrailService).record(
                "hublang.spv",
                "CONNECTION",
                "TERMINATE_CONNECTION",
                connection.getId().toString(),
                "berhenti langganan"
        );
    }

    @Test
    void rejectsTerminateForDraftConnection() {
        Connection connection = new Connection(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "SR-0001",
                "MTR-0001",
                LocalDate.of(2026, 7, 5)
        );
        when(connectionRepository.findById(connection.getId())).thenReturn(Optional.of(connection));

        assertThatThrownBy(() -> service.terminateConnection(
                connection.getId(),
                new ConnectionWorkflowRequest("berhenti").reason(),
                "hublang.spv"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Draft connection cannot be terminated");
    }
}
