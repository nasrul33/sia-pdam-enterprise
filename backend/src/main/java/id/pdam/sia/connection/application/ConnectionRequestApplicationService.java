package id.pdam.sia.connection.application;

import id.pdam.sia.connection.domain.ConnectionRequest;
import id.pdam.sia.connection.domain.ConnectionRequestStatus;
import id.pdam.sia.connection.repository.ConnectionRepository;
import id.pdam.sia.connection.repository.ConnectionRequestRepository;
import id.pdam.sia.customer.domain.CustomerHistory;
import id.pdam.sia.customer.repository.CustomerHistoryRepository;
import id.pdam.sia.customer.repository.CustomerRepository;
import id.pdam.sia.shared.audit.AuditTrailService;
import id.pdam.sia.shared.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class ConnectionRequestApplicationService {
    private static final DateTimeFormatter NUMBER_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final ConnectionRequestRepository connectionRequestRepository;
    private final CustomerRepository customerRepository;
    private final ConnectionRepository connectionRepository;
    private final CustomerHistoryRepository customerHistoryRepository;
    private final AuditTrailService auditTrailService;

    public ConnectionRequestApplicationService(
            ConnectionRequestRepository connectionRequestRepository,
            CustomerRepository customerRepository,
            ConnectionRepository connectionRepository,
            CustomerHistoryRepository customerHistoryRepository,
            AuditTrailService auditTrailService
    ) {
        this.connectionRequestRepository = connectionRequestRepository;
        this.customerRepository = customerRepository;
        this.connectionRepository = connectionRepository;
        this.customerHistoryRepository = customerHistoryRepository;
        this.auditTrailService = auditTrailService;
    }

    @Transactional(readOnly = true)
    public Page<ConnectionRequest> list(ConnectionRequestStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "requestedAt")
        );
        if (status == null) {
            return connectionRequestRepository.findAll(pageable);
        }
        return connectionRequestRepository.findByStatus(status, pageable);
    }

    @Transactional
    public ConnectionRequest submit(SubmitConnectionRequestCommand command, String actor) {
        if (command.customerId() != null && !customerRepository.existsById(command.customerId())) {
            throw new BusinessException("CONNECTION_REQUEST_CUSTOMER_NOT_FOUND", "Connection request customer was not found.");
        }
        String number = command.requestNumber() == null || command.requestNumber().isBlank()
                ? generatedNumber()
                : command.requestNumber().trim();
        connectionRequestRepository.findByRequestNumber(number).ifPresent(existing -> {
            throw new BusinessException("CONNECTION_REQUEST_NUMBER_DUPLICATE", "Connection request number already exists.");
        });
        ConnectionRequest request = connectionRequestRepository.save(new ConnectionRequest(
                number,
                command.customerId(),
                command.applicantName(),
                command.phoneNumber(),
                command.addressLine(),
                command.areaCode(),
                command.tariffGroupId()
        ));
        auditTrailService.record(actor, "CONNECTION", "SUBMIT_CONNECTION_REQUEST", request.getId().toString(), command.reason());
        return request;
    }

    @Transactional
    public ConnectionRequest survey(UUID requestId, WorkflowCommand command, String actor) {
        ConnectionRequest request = find(requestId);
        request.survey(command.notes());
        ConnectionRequest saved = connectionRequestRepository.save(request);
        auditTrailService.record(actor, "CONNECTION", "SURVEY_CONNECTION_REQUEST", saved.getId().toString(), command.reason());
        return saved;
    }

    @Transactional
    public ConnectionRequest approve(UUID requestId, WorkflowCommand command, String actor) {
        ConnectionRequest request = find(requestId);
        request.approve(command.reason(), actor);
        ConnectionRequest saved = connectionRequestRepository.save(request);
        auditTrailService.record(actor, "CONNECTION", "APPROVE_CONNECTION_REQUEST", saved.getId().toString(), command.reason());
        recordCustomerHistory(saved, "CONNECTION_REQUEST_APPROVED", null, saved.getRequestNumber(), command.reason(), actor);
        return saved;
    }

    @Transactional
    public ConnectionRequest reject(UUID requestId, WorkflowCommand command, String actor) {
        ConnectionRequest request = find(requestId);
        request.reject(command.reason(), actor);
        ConnectionRequest saved = connectionRequestRepository.save(request);
        auditTrailService.record(actor, "CONNECTION", "REJECT_CONNECTION_REQUEST", saved.getId().toString(), command.reason());
        recordCustomerHistory(saved, "CONNECTION_REQUEST_REJECTED", null, saved.getRequestNumber(), command.reason(), actor);
        return saved;
    }

    @Transactional
    public ConnectionRequest activate(UUID requestId, ActivateConnectionRequestCommand command, String actor) {
        ConnectionRequest request = find(requestId);
        if (!connectionRepository.existsById(command.connectionId())) {
            throw new BusinessException("CONNECTION_REQUEST_CONNECTION_NOT_FOUND", "Connection was not found.");
        }
        request.activate(command.connectionId(), actor);
        ConnectionRequest saved = connectionRequestRepository.save(request);
        auditTrailService.record(actor, "CONNECTION", "ACTIVATE_CONNECTION_REQUEST", saved.getId().toString(), command.reason());
        recordCustomerHistory(saved, "CONNECTION_REQUEST_ACTIVATED", null, saved.getRequestNumber(), command.reason(), actor);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<CustomerHistory> customerHistory(UUID customerId) {
        if (customerId == null || !customerRepository.existsById(customerId)) {
            throw new BusinessException("CUSTOMER_NOT_FOUND", "Customer was not found.");
        }
        return customerHistoryRepository.findByCustomerIdOrderByChangedAtDesc(customerId);
    }

    private ConnectionRequest find(UUID requestId) {
        if (requestId == null) {
            throw new BusinessException("CONNECTION_REQUEST_ID_REQUIRED", "Connection request id is required.");
        }
        return connectionRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException("CONNECTION_REQUEST_NOT_FOUND", "Connection request was not found."));
    }

    private void recordCustomerHistory(ConnectionRequest request, String type, String before, String after, String reason, String actor) {
        if (request.getCustomerId() == null) {
            return;
        }
        customerHistoryRepository.save(new CustomerHistory(request.getCustomerId(), type, before, after, reason, actor));
    }

    private static String generatedNumber() {
        return "CR-" + NUMBER_DATE.format(LocalDate.now()) + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public record SubmitConnectionRequestCommand(
            String requestNumber,
            UUID customerId,
            String applicantName,
            String phoneNumber,
            String addressLine,
            String areaCode,
            UUID tariffGroupId,
            String reason
    ) {
    }

    public record WorkflowCommand(String notes, String reason) {
    }

    public record ActivateConnectionRequestCommand(UUID connectionId, String reason) {
    }
}
