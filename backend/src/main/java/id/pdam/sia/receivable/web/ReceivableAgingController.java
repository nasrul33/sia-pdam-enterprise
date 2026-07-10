package id.pdam.sia.receivable.web;

import id.pdam.sia.receivable.application.ReceivableAgingApplicationService;
import id.pdam.sia.shared.security.Permissions;
import id.pdam.sia.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
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
public class ReceivableAgingController {
    private final ReceivableAgingApplicationService receivableAgingApplicationService;

    public ReceivableAgingController(ReceivableAgingApplicationService receivableAgingApplicationService) {
        this.receivableAgingApplicationService = receivableAgingApplicationService;
    }

    @GetMapping("/receivable-aging-snapshots")
    @PreAuthorize(Permissions.RECEIVABLE_AGING_READ)
    public PageResponse<ReceivableAgingSnapshotResponse> listSnapshots(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        return PageResponse.from(
                receivableAgingApplicationService.listSnapshots(page, size).map(ReceivableAgingSnapshotResponse::from)
        );
    }

    @GetMapping("/receivable-aging-snapshots/{snapshotId}")
    @PreAuthorize(Permissions.RECEIVABLE_AGING_READ)
    public ReceivableAgingSnapshotResponse getSnapshot(@PathVariable UUID snapshotId) {
        return ReceivableAgingSnapshotResponse.from(receivableAgingApplicationService.getSnapshot(snapshotId));
    }

    @GetMapping("/receivable-aging-snapshots/by-period/{period}")
    @PreAuthorize(Permissions.RECEIVABLE_AGING_READ)
    public ReceivableAgingSnapshotResponse getSnapshotByPeriod(
            @PathVariable @Pattern(regexp = "^\\d{4}-\\d{2}$") String period
    ) {
        return ReceivableAgingSnapshotResponse.from(receivableAgingApplicationService.getSnapshotByPeriod(period));
    }

    @PostMapping("/receivable-aging-snapshots/generate")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.RECEIVABLE_AGING_GENERATE)
    public ReceivableAgingSnapshotResponse generateSnapshot(
            @Valid @RequestBody GenerateReceivableAgingSnapshotRequest request,
            Principal principal
    ) {
        return ReceivableAgingSnapshotResponse.from(
                receivableAgingApplicationService.generateSnapshot(request, actor(principal))
        );
    }

    private static String actor(Principal principal) {
        return principal == null ? "system" : principal.getName();
    }
}
