package id.pdam.sia.customer.application;

import id.pdam.sia.customer.domain.Customer;
import id.pdam.sia.customer.domain.CustomerStatus;
import id.pdam.sia.customer.repository.CustomerRepository;
import id.pdam.sia.customer.web.CreateCustomerRequest;
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
public class CustomerApplicationService {
    private static final int MAX_PAGE_SIZE = 100;

    private final CustomerRepository customerRepository;
    private final AuditTrailService auditTrailService;

    public CustomerApplicationService(CustomerRepository customerRepository, AuditTrailService auditTrailService) {
        this.customerRepository = customerRepository;
        this.auditTrailService = auditTrailService;
    }

    @Transactional(readOnly = true)
    public Page<Customer> listCustomers(CustomerStatus status, String search, int page, int size) {
        Pageable pageable = pageable(page, size, Sort.by("customerNumber").ascending());
        String normalizedSearch = normalize(search);

        if (status != null && normalizedSearch != null) {
            return customerRepository.findByStatusAndFullNameContainingIgnoreCase(status, normalizedSearch, pageable);
        }
        if (status != null) {
            return customerRepository.findByStatus(status, pageable);
        }
        if (normalizedSearch != null) {
            return customerRepository.findByFullNameContainingIgnoreCase(normalizedSearch, pageable);
        }
        return customerRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Customer getCustomer(UUID customerId) {
        return customerRepository.findWithAddressesById(customerId)
                .orElseThrow(() -> new BusinessException("CUSTOMER_NOT_FOUND", "Customer was not found."));
    }

    @Transactional
    public Customer createCustomer(CreateCustomerRequest request, String actor) {
        String customerNumber = requireNormalize(
                request.customerNumber(),
                "CUSTOMER_NUMBER_REQUIRED",
                "Customer number is required."
        );
        customerRepository.findByCustomerNumber(customerNumber).ifPresent(existing -> {
            throw new BusinessException("CUSTOMER_NUMBER_DUPLICATE", "Customer number already exists.");
        });

        Customer customer = new Customer(
                customerNumber,
                request.fullName(),
                request.identityNumber(),
                request.phoneNumber()
        );
        customer.addAddress(request.addressLine(), request.areaCode(), request.latitude(), request.longitude());

        Customer saved = customerRepository.save(customer);
        auditTrailService.record(actor, "CUSTOMER", "CREATE_CUSTOMER", saved.getId().toString(), request.reason());
        return saved;
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
        String normalized = normalize(value);
        if (normalized == null) {
            throw new BusinessException(code, message);
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
