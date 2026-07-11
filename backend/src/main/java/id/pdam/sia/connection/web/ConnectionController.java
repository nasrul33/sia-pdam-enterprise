package id.pdam.sia.connection.web;

import id.pdam.sia.connection.application.ConnectionApplicationService;
import id.pdam.sia.connection.domain.ConnectionStatus;
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
@RequestMapping("/api")
public class ConnectionController {
    private final ConnectionApplicationService connectionApplicationService;

    public ConnectionController(ConnectionApplicationService connectionApplicationService) {
        this.connectionApplicationService = connectionApplicationService;
    }

    @GetMapping("/tariff-groups")
    @PreAuthorize(Permissions.TARIFF_READ)
    public PageResponse<TariffGroupResponse> listTariffGroups(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        return PageResponse.from(connectionApplicationService.listTariffGroups(search, page, size).map(TariffGroupResponse::from));
    }

    @PostMapping("/tariff-groups")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.TARIFF_MANAGE)
    public TariffGroupResponse createTariffGroup(
            @Valid @RequestBody CreateTariffGroupRequest request,
            Principal principal
    ) {
        return TariffGroupResponse.from(connectionApplicationService.createTariffGroup(request, actor(principal)));
    }

    @GetMapping("/connections")
    @PreAuthorize(Permissions.CONNECTION_READ)
    public PageResponse<ConnectionResponse> listConnections(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) ConnectionStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        return PageResponse.from(
                connectionApplicationService.listConnections(customerId, status, search, page, size).map(ConnectionResponse::from)
        );
    }

    @GetMapping("/connections/{connectionId}")
    @PreAuthorize(Permissions.CONNECTION_READ)
    public ConnectionResponse getConnection(@PathVariable UUID connectionId) {
        return ConnectionResponse.from(connectionApplicationService.getConnection(connectionId));
    }

    @PostMapping("/connections")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.CONNECTION_MANAGE)
    public ConnectionResponse createConnection(@Valid @RequestBody CreateConnectionRequest request, Principal principal) {
        return ConnectionResponse.from(connectionApplicationService.createConnection(request, actor(principal)));
    }

    @PostMapping("/connections/{connectionId}/activate")
    @PreAuthorize(Permissions.CONNECTION_MANAGE)
    public ConnectionResponse activateConnection(
            @PathVariable UUID connectionId,
            @Valid @RequestBody ConnectionWorkflowRequest request,
            Principal principal
    ) {
        return ConnectionResponse.from(
                connectionApplicationService.activateConnection(connectionId, request.reason(), actor(principal))
        );
    }

    @PostMapping("/connections/{connectionId}/suspend")
    @PreAuthorize(Permissions.CONNECTION_MANAGE)
    public ConnectionResponse suspendConnection(
            @PathVariable UUID connectionId,
            @Valid @RequestBody ConnectionWorkflowRequest request,
            Principal principal
    ) {
        return ConnectionResponse.from(
                connectionApplicationService.suspendConnection(connectionId, request.reason(), actor(principal))
        );
    }

    @PostMapping("/connections/{connectionId}/terminate")
    @PreAuthorize(Permissions.CONNECTION_MANAGE)
    public ConnectionResponse terminateConnection(
            @PathVariable UUID connectionId,
            @Valid @RequestBody ConnectionWorkflowRequest request,
            Principal principal
    ) {
        return ConnectionResponse.from(
                connectionApplicationService.terminateConnection(connectionId, request.reason(), actor(principal))
        );
    }

    private static String actor(Principal principal) {
        return principal == null ? "system" : principal.getName();
    }
}
