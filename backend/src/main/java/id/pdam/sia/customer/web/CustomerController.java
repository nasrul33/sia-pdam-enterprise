package id.pdam.sia.customer.web;

import id.pdam.sia.customer.application.CustomerApplicationService;
import id.pdam.sia.customer.domain.CustomerStatus;
import id.pdam.sia.shared.security.Permissions;
import id.pdam.sia.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    private final CustomerApplicationService customerApplicationService;

    public CustomerController(CustomerApplicationService customerApplicationService) {
        this.customerApplicationService = customerApplicationService;
    }

    @GetMapping
    @PreAuthorize(Permissions.CUSTOMER_READ)
    public PageResponse<CustomerSummaryResponse> listCustomers(
            @RequestParam(required = false) CustomerStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        return PageResponse.from(
                customerApplicationService.listCustomers(status, search, page, size).map(CustomerSummaryResponse::from)
        );
    }

    @GetMapping("/{customerId}")
    @PreAuthorize(Permissions.CUSTOMER_READ)
    public CustomerResponse getCustomer(@PathVariable UUID customerId) {
        return CustomerResponse.from(customerApplicationService.getCustomer(customerId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.CUSTOMER_MANAGE)
    public CustomerResponse createCustomer(@Valid @RequestBody CreateCustomerRequest request, Principal principal) {
        return CustomerResponse.from(customerApplicationService.createCustomer(request, actor(principal)));
    }

    private static String actor(Principal principal) {
        return principal == null ? "system" : principal.getName();
    }
}
