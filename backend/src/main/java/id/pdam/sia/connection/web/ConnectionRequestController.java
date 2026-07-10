package id.pdam.sia.connection.web;

import id.pdam.sia.connection.application.ConnectionRequestApplicationService;
import id.pdam.sia.connection.domain.ConnectionRequest;
import id.pdam.sia.connection.domain.ConnectionRequestStatus;
import id.pdam.sia.customer.domain.CustomerHistory;
import id.pdam.sia.shared.security.Permissions;
import id.pdam.sia.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api")
public class ConnectionRequestController {
    private final ConnectionRequestApplicationService service;

    public ConnectionRequestController(ConnectionRequestApplicationService service) {
        this.service = service;
    }

    @GetMapping("/connection-requests")
    @PreAuthorize(Permissions.CONNECTION_REQUEST_MANAGE)
    public PageResponse<ConnectionRequestResponse> listRequests(
            @RequestParam(required = false) ConnectionRequestStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        return PageResponse.from(service.list(status, page, size).map(ConnectionRequestResponse::from));
    }

    @PostMapping("/connection-requests")
    @PreAuthorize(Permissions.CONNECTION_REQUEST_MANAGE)
    public ConnectionRequestResponse submit(@Valid @RequestBody SubmitConnectionRequestRequest request, Principal principal) {
        return ConnectionRequestResponse.from(service.submit(request.toCommand(), actor(principal)));
    }

    @PostMapping("/connection-requests/{requestId}/survey")
    @PreAuthorize(Permissions.CONNECTION_REQUEST_MANAGE)
    public ConnectionRequestResponse survey(
            @PathVariable UUID requestId,
            @Valid @RequestBody WorkflowRequest request,
            Principal principal
    ) {
        return ConnectionRequestResponse.from(service.survey(requestId, request.toCommand(), actor(principal)));
    }

    @PostMapping("/connection-requests/{requestId}/approve")
    @PreAuthorize(Permissions.CONNECTION_REQUEST_MANAGE)
    public ConnectionRequestResponse approve(
            @PathVariable UUID requestId,
            @Valid @RequestBody WorkflowRequest request,
            Principal principal
    ) {
        return ConnectionRequestResponse.from(service.approve(requestId, request.toCommand(), actor(principal)));
    }

    @PostMapping("/connection-requests/{requestId}/reject")
    @PreAuthorize(Permissions.CONNECTION_REQUEST_MANAGE)
    public ConnectionRequestResponse reject(
            @PathVariable UUID requestId,
            @Valid @RequestBody WorkflowRequest request,
            Principal principal
    ) {
        return ConnectionRequestResponse.from(service.reject(requestId, request.toCommand(), actor(principal)));
    }

    @PostMapping("/connection-requests/{requestId}/activate")
    @PreAuthorize(Permissions.CONNECTION_REQUEST_MANAGE)
    public ConnectionRequestResponse activate(
            @PathVariable UUID requestId,
            @Valid @RequestBody ActivateConnectionRequestRequest request,
            Principal principal
    ) {
        return ConnectionRequestResponse.from(service.activate(requestId, request.toCommand(), actor(principal)));
    }

    @GetMapping("/customers/{customerId}/history")
    @PreAuthorize(Permissions.CUSTOMER_HISTORY_READ)
    public List<CustomerHistoryResponse> customerHistory(@PathVariable UUID customerId) {
        return service.customerHistory(customerId).stream().map(CustomerHistoryResponse::from).toList();
    }

    private static String actor(Principal principal) {
        return principal == null ? "system" : principal.getName();
    }

    public record SubmitConnectionRequestRequest(
            String requestNumber,
            UUID customerId,
            @NotBlank String applicantName,
            String phoneNumber,
            @NotBlank String addressLine,
            @NotBlank String areaCode,
            UUID tariffGroupId,
            @NotBlank String reason
    ) {
        private ConnectionRequestApplicationService.SubmitConnectionRequestCommand toCommand() {
            return new ConnectionRequestApplicationService.SubmitConnectionRequestCommand(
                    requestNumber,
                    customerId,
                    applicantName,
                    phoneNumber,
                    addressLine,
                    areaCode,
                    tariffGroupId,
                    reason
            );
        }
    }

    public record WorkflowRequest(String notes, @NotBlank String reason) {
        private ConnectionRequestApplicationService.WorkflowCommand toCommand() {
            return new ConnectionRequestApplicationService.WorkflowCommand(notes, reason);
        }
    }

    public record ActivateConnectionRequestRequest(@NotNull UUID connectionId, @NotBlank String reason) {
        private ConnectionRequestApplicationService.ActivateConnectionRequestCommand toCommand() {
            return new ConnectionRequestApplicationService.ActivateConnectionRequestCommand(connectionId, reason);
        }
    }

    public record ConnectionRequestResponse(
            UUID id,
            String requestNumber,
            UUID customerId,
            String applicantName,
            String phoneNumber,
            String addressLine,
            String areaCode,
            ConnectionRequestStatus status,
            UUID tariffGroupId,
            String surveyNotes,
            String decisionReason,
            Instant requestedAt,
            Instant decidedAt,
            String decidedBy,
            UUID activatedConnectionId,
            Instant createdAt,
            Instant updatedAt
    ) {
        private static ConnectionRequestResponse from(ConnectionRequest request) {
            return new ConnectionRequestResponse(
                    request.getId(),
                    request.getRequestNumber(),
                    request.getCustomerId(),
                    request.getApplicantName(),
                    request.getPhoneNumber(),
                    request.getAddressLine(),
                    request.getAreaCode(),
                    request.getStatus(),
                    request.getTariffGroupId(),
                    request.getSurveyNotes(),
                    request.getDecisionReason(),
                    request.getRequestedAt(),
                    request.getDecidedAt(),
                    request.getDecidedBy(),
                    request.getActivatedConnectionId(),
                    request.getCreatedAt(),
                    request.getUpdatedAt()
            );
        }
    }

    public record CustomerHistoryResponse(
            UUID id,
            UUID customerId,
            String changeType,
            String beforeValue,
            String afterValue,
            String reason,
            String changedBy,
            Instant changedAt
    ) {
        private static CustomerHistoryResponse from(CustomerHistory history) {
            return new CustomerHistoryResponse(
                    history.getId(),
                    history.getCustomerId(),
                    history.getChangeType(),
                    history.getBeforeValue(),
                    history.getAfterValue(),
                    history.getReason(),
                    history.getChangedBy(),
                    history.getChangedAt()
            );
        }
    }
}
