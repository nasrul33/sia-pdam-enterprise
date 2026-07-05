package id.pdam.sia.billing.web;

import id.pdam.sia.billing.application.TariffEngineApplicationService;
import id.pdam.sia.billing.domain.TariffVersionStatus;
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
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api")
public class TariffController {
    private final TariffEngineApplicationService tariffEngineApplicationService;

    public TariffController(TariffEngineApplicationService tariffEngineApplicationService) {
        this.tariffEngineApplicationService = tariffEngineApplicationService;
    }

    @GetMapping("/tariff-versions")
    public PageResponse<TariffVersionResponse> listVersions(
            @RequestParam(required = false) UUID tariffGroupId,
            @RequestParam(required = false) TariffVersionStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        return PageResponse.from(
                tariffEngineApplicationService.listVersions(tariffGroupId, status, page, size).map(TariffVersionResponse::from)
        );
    }

    @GetMapping("/tariff-versions/{tariffVersionId}")
    public TariffVersionResponse getVersion(@PathVariable UUID tariffVersionId) {
        return TariffVersionResponse.from(tariffEngineApplicationService.getVersion(tariffVersionId));
    }

    @PostMapping("/tariff-versions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public TariffVersionResponse createVersion(
            @Valid @RequestBody CreateTariffVersionRequest request,
            Principal principal
    ) {
        return TariffVersionResponse.from(tariffEngineApplicationService.createVersion(request, actor(principal)));
    }

    @GetMapping("/tariff-versions/{tariffVersionId}/blocks")
    public List<TariffBlockResponse> listBlocks(@PathVariable UUID tariffVersionId) {
        return tariffEngineApplicationService.listBlocks(tariffVersionId).stream()
                .map(TariffBlockResponse::from)
                .toList();
    }

    @PostMapping("/tariff-versions/{tariffVersionId}/blocks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public TariffBlockResponse addBlock(
            @PathVariable UUID tariffVersionId,
            @Valid @RequestBody CreateTariffBlockRequest request,
            Principal principal
    ) {
        return TariffBlockResponse.from(tariffEngineApplicationService.addBlock(tariffVersionId, request, actor(principal)));
    }

    @PostMapping("/tariff-versions/{tariffVersionId}/activate")
    @PreAuthorize("isAuthenticated()")
    public TariffVersionResponse activateVersion(
            @PathVariable UUID tariffVersionId,
            @Valid @RequestBody TariffWorkflowRequest request,
            Principal principal
    ) {
        return TariffVersionResponse.from(
                tariffEngineApplicationService.activateVersion(tariffVersionId, request.reason(), actor(principal))
        );
    }

    @PostMapping("/tariff-versions/{tariffVersionId}/archive")
    @PreAuthorize("isAuthenticated()")
    public TariffVersionResponse archiveVersion(
            @PathVariable UUID tariffVersionId,
            @Valid @RequestBody TariffWorkflowRequest request,
            Principal principal
    ) {
        return TariffVersionResponse.from(
                tariffEngineApplicationService.archiveVersion(tariffVersionId, request.reason(), actor(principal))
        );
    }

    @PostMapping("/tariff-calculations")
    public TariffCalculationResponse calculate(@Valid @RequestBody CalculateTariffRequest request) {
        return TariffCalculationResponse.from(tariffEngineApplicationService.calculate(request));
    }

    private static String actor(Principal principal) {
        return principal == null ? "system" : principal.getName();
    }
}
