package id.pdam.sia.receivable.web;

import id.pdam.sia.receivable.application.CollectionActionApplicationService;
import id.pdam.sia.receivable.domain.CollectionActionStatus;
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
@RequestMapping("/api/collection-actions")
public class CollectionActionController {
    private final CollectionActionApplicationService collectionActionApplicationService;

    public CollectionActionController(CollectionActionApplicationService collectionActionApplicationService) {
        this.collectionActionApplicationService = collectionActionApplicationService;
    }

    @GetMapping
    @PreAuthorize(Permissions.COLLECTION_ACTION_READ)
    public PageResponse<CollectionActionResponse> listActions(
            @RequestParam(required = false) CollectionActionStatus status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID invoiceId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        return PageResponse.from(
                collectionActionApplicationService
                        .listActions(status, customerId, invoiceId, page, size)
                        .map(CollectionActionResponse::from)
        );
    }

    @GetMapping("/{actionId}")
    @PreAuthorize(Permissions.COLLECTION_ACTION_READ)
    public CollectionActionResponse getAction(@PathVariable UUID actionId) {
        return CollectionActionResponse.from(collectionActionApplicationService.getAction(actionId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.COLLECTION_ACTION_CREATE)
    public CollectionActionResponse createAction(
            @Valid @RequestBody CreateCollectionActionRequest request,
            Principal principal
    ) {
        return CollectionActionResponse.from(collectionActionApplicationService.createAction(request, actor(principal)));
    }

    @PostMapping("/{actionId}/start")
    @PreAuthorize(Permissions.COLLECTION_ACTION_EXECUTE)
    public CollectionActionResponse startAction(
            @PathVariable UUID actionId,
            @Valid @RequestBody CollectionActionWorkflowRequest request,
            Principal principal
    ) {
        return CollectionActionResponse.from(collectionActionApplicationService.startAction(actionId, request, actor(principal)));
    }

    @PostMapping("/{actionId}/complete")
    @PreAuthorize(Permissions.COLLECTION_ACTION_EXECUTE)
    public CollectionActionResponse completeAction(
            @PathVariable UUID actionId,
            @Valid @RequestBody CollectionActionWorkflowRequest request,
            Principal principal
    ) {
        return CollectionActionResponse.from(collectionActionApplicationService.completeAction(actionId, request, actor(principal)));
    }

    @PostMapping("/{actionId}/cancel")
    @PreAuthorize(Permissions.COLLECTION_ACTION_CANCEL)
    public CollectionActionResponse cancelAction(
            @PathVariable UUID actionId,
            @Valid @RequestBody CollectionActionWorkflowRequest request,
            Principal principal
    ) {
        return CollectionActionResponse.from(collectionActionApplicationService.cancelAction(actionId, request, actor(principal)));
    }

    private static String actor(Principal principal) {
        return principal == null ? "system" : principal.getName();
    }
}
