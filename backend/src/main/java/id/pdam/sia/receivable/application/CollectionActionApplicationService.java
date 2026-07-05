package id.pdam.sia.receivable.application;

import id.pdam.sia.billing.domain.Invoice;
import id.pdam.sia.billing.domain.InvoiceStatus;
import id.pdam.sia.billing.repository.InvoiceRepository;
import id.pdam.sia.customer.domain.Customer;
import id.pdam.sia.customer.domain.CustomerStatus;
import id.pdam.sia.customer.repository.CustomerRepository;
import id.pdam.sia.receivable.domain.CollectionAction;
import id.pdam.sia.receivable.domain.CollectionActionStatus;
import id.pdam.sia.receivable.domain.CollectionActionType;
import id.pdam.sia.receivable.repository.CollectionActionRepository;
import id.pdam.sia.receivable.web.CollectionActionWorkflowRequest;
import id.pdam.sia.receivable.web.CreateCollectionActionRequest;
import id.pdam.sia.shared.audit.AuditTrailService;
import id.pdam.sia.shared.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class CollectionActionApplicationService {
    private static final int MAX_PAGE_SIZE = 100;

    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final CollectionActionRepository collectionActionRepository;
    private final AuditTrailService auditTrailService;

    public CollectionActionApplicationService(
            CustomerRepository customerRepository,
            InvoiceRepository invoiceRepository,
            CollectionActionRepository collectionActionRepository,
            AuditTrailService auditTrailService
    ) {
        this.customerRepository = customerRepository;
        this.invoiceRepository = invoiceRepository;
        this.collectionActionRepository = collectionActionRepository;
        this.auditTrailService = auditTrailService;
    }

    @Transactional(readOnly = true)
    public Page<CollectionAction> listActions(
            CollectionActionStatus status,
            UUID customerId,
            UUID invoiceId,
            int page,
            int size
    ) {
        Specification<CollectionAction> specification = Specification.allOf();
        if (status != null) {
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("status"), status));
        }
        if (customerId != null) {
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("customerId"), customerId));
        }
        if (invoiceId != null) {
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("invoiceId"), invoiceId));
        }
        return collectionActionRepository.findAll(
                specification,
                pageable(page, size, Sort.by("scheduledAt").descending().and(Sort.by("createdAt").descending()))
        );
    }

    @Transactional(readOnly = true)
    public CollectionAction getAction(UUID actionId) {
        return findAction(actionId);
    }

    @Transactional
    public CollectionAction createAction(CreateCollectionActionRequest request, String actor) {
        if (request == null) {
            throw new BusinessException("COLLECTION_ACTION_REQUEST_REQUIRED", "Collection action request is required.");
        }
        UUID customerId = requireUuid(request.customerId(), "COLLECTION_ACTION_CUSTOMER_REQUIRED", "Collection action customer is required.");
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException("COLLECTION_ACTION_CUSTOMER_NOT_FOUND", "Collection action customer was not found."));
        if (customer.getStatus() != CustomerStatus.ACTIVE) {
            throw new BusinessException("COLLECTION_ACTION_CUSTOMER_INACTIVE", "Collection action customer must be active.");
        }
        CollectionActionType actionType = requireActionType(request.actionType());
        Instant scheduledAt = requireInstant(request.scheduledAt(), "COLLECTION_ACTION_SCHEDULE_REQUIRED", "Collection action schedule is required.");
        String reason = requireNormalize(request.reason(), "COLLECTION_ACTION_REASON_REQUIRED", "Collection action reason is required.");
        UUID invoiceId = request.invoiceId();

        if (actionType.requiresInvoice() && invoiceId == null) {
            throw new BusinessException("COLLECTION_ACTION_INVOICE_REQUIRED", "Dunning action requires an invoice.");
        }
        if (invoiceId != null) {
            Invoice invoice = invoiceRepository.findById(invoiceId)
                    .orElseThrow(() -> new BusinessException("COLLECTION_ACTION_INVOICE_NOT_FOUND", "Collection action invoice was not found."));
            validateOverdueOpenInvoice(invoice, scheduledAt);
            ensureNoActiveInvoiceAction(invoiceId, actionType);
        } else {
            ensureNoActiveCustomerAction(customerId, actionType);
        }

        CollectionAction action = new CollectionAction(
                customerId,
                invoiceId,
                actionType,
                scheduledAt,
                request.notes()
        );
        CollectionAction saved = collectionActionRepository.save(action);
        auditTrailService.record(actor, "RECEIVABLE", "CREATE_COLLECTION_ACTION", saved.getId().toString(), reason);
        return saved;
    }

    @Transactional
    public CollectionAction startAction(UUID actionId, CollectionActionWorkflowRequest request, String actor) {
        WorkflowCommand command = workflowCommand(request);
        CollectionAction action = findAction(actionId);
        action.start(Instant.now());
        action.updateNotes(command.notes());
        CollectionAction saved = collectionActionRepository.save(action);
        auditTrailService.record(actor, "RECEIVABLE", "START_COLLECTION_ACTION", saved.getId().toString(), command.reason());
        return saved;
    }

    @Transactional
    public CollectionAction completeAction(UUID actionId, CollectionActionWorkflowRequest request, String actor) {
        WorkflowCommand command = workflowCommand(request);
        CollectionAction action = findAction(actionId);
        action.complete(Instant.now(), command.notes());
        CollectionAction saved = collectionActionRepository.save(action);
        auditTrailService.record(actor, "RECEIVABLE", "COMPLETE_COLLECTION_ACTION", saved.getId().toString(), command.reason());
        return saved;
    }

    @Transactional
    public CollectionAction cancelAction(UUID actionId, CollectionActionWorkflowRequest request, String actor) {
        WorkflowCommand command = workflowCommand(request);
        CollectionAction action = findAction(actionId);
        action.cancel(command.notes());
        CollectionAction saved = collectionActionRepository.save(action);
        auditTrailService.record(actor, "RECEIVABLE", "CANCEL_COLLECTION_ACTION", saved.getId().toString(), command.reason());
        return saved;
    }

    private CollectionAction findAction(UUID actionId) {
        UUID normalizedActionId = requireUuid(actionId, "COLLECTION_ACTION_ID_REQUIRED", "Collection action id is required.");
        return collectionActionRepository.findById(normalizedActionId)
                .orElseThrow(() -> new BusinessException("COLLECTION_ACTION_NOT_FOUND", "Collection action was not found."));
    }

    private void ensureNoActiveInvoiceAction(UUID invoiceId, CollectionActionType actionType) {
        if (collectionActionRepository.existsByInvoiceIdAndActionTypeAndStatusIn(
                invoiceId,
                actionType,
                CollectionActionStatus.activeStatuses()
        )) {
            throw new BusinessException(
                    "COLLECTION_ACTION_ACTIVE_DUPLICATE",
                    "Invoice already has an active collection action for this type."
            );
        }
    }

    private void ensureNoActiveCustomerAction(UUID customerId, CollectionActionType actionType) {
        if (collectionActionRepository.existsByCustomerIdAndInvoiceIdIsNullAndActionTypeAndStatusIn(
                customerId,
                actionType,
                CollectionActionStatus.activeStatuses()
        )) {
            throw new BusinessException(
                    "COLLECTION_ACTION_ACTIVE_DUPLICATE",
                    "Customer already has an active collection action for this type."
            );
        }
    }

    private static void validateOverdueOpenInvoice(Invoice invoice, Instant scheduledAt) {
        if (invoice.getStatus() != InvoiceStatus.ISSUED && invoice.getStatus() != InvoiceStatus.PARTIAL_PAID) {
            throw new BusinessException("COLLECTION_ACTION_INVOICE_STATUS_INVALID", "Collection action invoice must be an open receivable.");
        }
        if (invoice.getOutstandingAmount() == null || invoice.getOutstandingAmount().signum() <= 0) {
            throw new BusinessException("COLLECTION_ACTION_INVOICE_PAID", "Collection action invoice must have outstanding amount.");
        }
        LocalDate scheduledDate = scheduledAt.atZone(ZoneOffset.UTC).toLocalDate();
        if (invoice.getDueDate() == null || !invoice.getDueDate().isBefore(scheduledDate)) {
            throw new BusinessException("COLLECTION_ACTION_INVOICE_NOT_OVERDUE", "Collection action invoice must be overdue on the schedule date.");
        }
    }

    private static WorkflowCommand workflowCommand(CollectionActionWorkflowRequest request) {
        if (request == null) {
            throw new BusinessException("COLLECTION_ACTION_WORKFLOW_REQUEST_REQUIRED", "Collection action workflow request is required.");
        }
        return new WorkflowCommand(
                normalize(request.notes()),
                requireNormalize(request.reason(), "COLLECTION_ACTION_REASON_REQUIRED", "Collection action reason is required.")
        );
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

    private static UUID requireUuid(UUID value, String code, String message) {
        if (value == null) {
            throw new BusinessException(code, message);
        }
        return value;
    }

    private static Instant requireInstant(Instant value, String code, String message) {
        if (value == null) {
            throw new BusinessException(code, message);
        }
        return value;
    }

    private static CollectionActionType requireActionType(CollectionActionType value) {
        if (value == null) {
            throw new BusinessException("COLLECTION_ACTION_TYPE_REQUIRED", "Collection action type is required.");
        }
        return value;
    }

    private static String requireNormalize(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record WorkflowCommand(String notes, String reason) {
    }
}
