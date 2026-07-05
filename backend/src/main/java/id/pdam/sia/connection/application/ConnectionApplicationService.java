package id.pdam.sia.connection.application;

import id.pdam.sia.connection.domain.Connection;
import id.pdam.sia.connection.domain.ConnectionStatus;
import id.pdam.sia.connection.domain.TariffGroup;
import id.pdam.sia.connection.repository.ConnectionRepository;
import id.pdam.sia.connection.repository.TariffGroupRepository;
import id.pdam.sia.connection.web.CreateConnectionRequest;
import id.pdam.sia.connection.web.CreateTariffGroupRequest;
import id.pdam.sia.customer.domain.Customer;
import id.pdam.sia.customer.domain.CustomerStatus;
import id.pdam.sia.customer.repository.CustomerRepository;
import id.pdam.sia.shared.audit.AuditTrailService;
import id.pdam.sia.shared.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ConnectionApplicationService {
    private static final int MAX_PAGE_SIZE = 100;

    private final ConnectionRepository connectionRepository;
    private final TariffGroupRepository tariffGroupRepository;
    private final CustomerRepository customerRepository;
    private final AuditTrailService auditTrailService;

    public ConnectionApplicationService(
            ConnectionRepository connectionRepository,
            TariffGroupRepository tariffGroupRepository,
            CustomerRepository customerRepository,
            AuditTrailService auditTrailService
    ) {
        this.connectionRepository = connectionRepository;
        this.tariffGroupRepository = tariffGroupRepository;
        this.customerRepository = customerRepository;
        this.auditTrailService = auditTrailService;
    }

    @Transactional(readOnly = true)
    public Page<TariffGroup> listTariffGroups(int page, int size) {
        return tariffGroupRepository.findAll(pageable(page, size, Sort.by("code").ascending()));
    }

    @Transactional
    public TariffGroup createTariffGroup(CreateTariffGroupRequest request, String actor) {
        String code = requireNormalize(
                request.code(),
                "TARIFF_GROUP_CODE_REQUIRED",
                "Tariff group code is required."
        );
        tariffGroupRepository.findByCode(code).ifPresent(existing -> {
            throw new BusinessException("TARIFF_GROUP_CODE_DUPLICATE", "Tariff group code already exists.");
        });

        TariffGroup tariffGroup = tariffGroupRepository.save(new TariffGroup(code, request.name()));
        auditTrailService.record(actor, "CONNECTION", "CREATE_TARIFF_GROUP", tariffGroup.getId().toString(), request.reason());
        return tariffGroup;
    }

    @Transactional(readOnly = true)
    public Page<Connection> listConnections(UUID customerId, ConnectionStatus status, int page, int size) {
        Pageable pageable = pageable(page, size, Sort.by("connectionNumber").ascending());
        if (customerId != null && status != null) {
            return connectionRepository.findByCustomerIdAndStatus(customerId, status, pageable);
        }
        if (customerId != null) {
            return connectionRepository.findByCustomerId(customerId, pageable);
        }
        if (status != null) {
            return connectionRepository.findByStatus(status, pageable);
        }
        return connectionRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Connection getConnection(UUID connectionId) {
        return connectionRepository.findById(connectionId)
                .orElseThrow(() -> new BusinessException("CONNECTION_NOT_FOUND", "Connection was not found."));
    }

    @Transactional
    public Connection createConnection(CreateConnectionRequest request, String actor) {
        String connectionNumber = requireNormalize(
                request.connectionNumber(),
                "CONNECTION_NUMBER_REQUIRED",
                "Connection number is required."
        );
        connectionRepository.findByConnectionNumber(connectionNumber).ifPresent(existing -> {
            throw new BusinessException("CONNECTION_NUMBER_DUPLICATE", "Connection number already exists.");
        });

        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new BusinessException("CUSTOMER_NOT_FOUND", "Customer was not found."));
        if (customer.getStatus() != CustomerStatus.ACTIVE) {
            throw new BusinessException("CUSTOMER_NOT_ACTIVE", "Connection can only be created for active customer.");
        }

        if (!tariffGroupRepository.existsById(request.tariffGroupId())) {
            throw new BusinessException("TARIFF_GROUP_NOT_FOUND", "Tariff group was not found.");
        }

        Connection connection = connectionRepository.save(new Connection(
                request.customerId(),
                request.tariffGroupId(),
                connectionNumber,
                request.meterNumber(),
                request.installedAt()
        ));
        auditTrailService.record(actor, "CONNECTION", "CREATE_CONNECTION", connection.getId().toString(), request.reason());
        return connection;
    }

    @Transactional
    public Connection activateConnection(UUID connectionId, String reason, String actor) {
        Connection connection = getConnectionForMutation(connectionId);
        Customer customer = customerRepository.findById(connection.getCustomerId())
                .orElseThrow(() -> new BusinessException("CUSTOMER_NOT_FOUND", "Customer was not found."));
        if (customer.getStatus() != CustomerStatus.ACTIVE) {
            throw new BusinessException("CUSTOMER_NOT_ACTIVE", "Connection can only be activated for active customer.");
        }
        connection.activate();
        auditTrailService.record(actor, "CONNECTION", "ACTIVATE_CONNECTION", connection.getId().toString(), reason);
        return connection;
    }

    @Transactional
    public Connection suspendConnection(UUID connectionId, String reason, String actor) {
        Connection connection = getConnectionForMutation(connectionId);
        connection.suspend();
        auditTrailService.record(actor, "CONNECTION", "SUSPEND_CONNECTION", connection.getId().toString(), reason);
        return connection;
    }

    @Transactional
    public Connection terminateConnection(UUID connectionId, String reason, String actor) {
        Connection connection = getConnectionForMutation(connectionId);
        connection.terminate();
        auditTrailService.record(actor, "CONNECTION", "TERMINATE_CONNECTION", connection.getId().toString(), reason);
        return connection;
    }

    private Connection getConnectionForMutation(UUID connectionId) {
        return connectionRepository.findById(connectionId)
                .orElseThrow(() -> new BusinessException("CONNECTION_NOT_FOUND", "Connection was not found."));
    }

    private static Pageable pageable(int page, int size, Sort sort) {
        if (page < 0) {
            throw new BusinessException("PAGE_INVALID", "Page must be zero or greater.");
        }
        if (size < 1) {
            throw new BusinessException("PAGE_SIZE_INVALID", "Page size must be at least one.");
        }
        return PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), sort);
    }

    private static String requireNormalize(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }
}
