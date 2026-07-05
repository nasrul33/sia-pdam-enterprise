package id.pdam.sia.metering.web;

import id.pdam.sia.metering.application.MeteringApplicationService;
import id.pdam.sia.metering.domain.MeterReadingStatus;
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
public class MeteringController {
    private final MeteringApplicationService meteringApplicationService;

    public MeteringController(MeteringApplicationService meteringApplicationService) {
        this.meteringApplicationService = meteringApplicationService;
    }

    @GetMapping("/meter-routes")
    public PageResponse<MeterRouteResponse> listRoutes(
            @RequestParam(required = false) String areaCode,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        return PageResponse.from(meteringApplicationService.listRoutes(areaCode, page, size).map(MeterRouteResponse::from));
    }

    @GetMapping("/meter-routes/{routeId}")
    public MeterRouteResponse getRoute(@PathVariable UUID routeId) {
        return MeterRouteResponse.from(meteringApplicationService.getRoute(routeId));
    }

    @PostMapping("/meter-routes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public MeterRouteResponse createRoute(@Valid @RequestBody CreateMeterRouteRequest request, Principal principal) {
        return MeterRouteResponse.from(meteringApplicationService.createRoute(request, actor(principal)));
    }

    @GetMapping("/meter-readings")
    public PageResponse<MeterReadingResponse> listReadings(
            @RequestParam(required = false) UUID routeId,
            @RequestParam(required = false) @Pattern(regexp = "^\\d{4}-\\d{2}$") String period,
            @RequestParam(required = false) MeterReadingStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        return PageResponse.from(
                meteringApplicationService.listReadings(routeId, period, status, page, size).map(MeterReadingResponse::from)
        );
    }

    @GetMapping("/meter-readings/{readingId}")
    public MeterReadingResponse getReading(@PathVariable UUID readingId) {
        return MeterReadingResponse.from(meteringApplicationService.getReading(readingId));
    }

    @PostMapping("/meter-readings")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public MeterReadingResponse createReading(@Valid @RequestBody CreateMeterReadingRequest request, Principal principal) {
        return MeterReadingResponse.from(meteringApplicationService.createReading(request, actor(principal)));
    }

    @PostMapping("/meter-readings/{readingId}/submit")
    @PreAuthorize("isAuthenticated()")
    public MeterReadingResponse submitReading(
            @PathVariable UUID readingId,
            @Valid @RequestBody MeteringWorkflowRequest request,
            Principal principal
    ) {
        return MeterReadingResponse.from(
                meteringApplicationService.submitReading(readingId, request.reason(), actor(principal))
        );
    }

    @PostMapping("/meter-readings/{readingId}/verify")
    @PreAuthorize("isAuthenticated()")
    public MeterReadingResponse verifyReading(
            @PathVariable UUID readingId,
            @Valid @RequestBody MeteringWorkflowRequest request,
            Principal principal
    ) {
        return MeterReadingResponse.from(
                meteringApplicationService.verifyReading(readingId, request.reason(), actor(principal))
        );
    }

    @PostMapping("/meter-readings/{readingId}/reject")
    @PreAuthorize("isAuthenticated()")
    public MeterReadingResponse rejectReading(
            @PathVariable UUID readingId,
            @Valid @RequestBody MeteringWorkflowRequest request,
            Principal principal
    ) {
        return MeterReadingResponse.from(
                meteringApplicationService.rejectReading(readingId, request.reason(), actor(principal))
        );
    }

    private static String actor(Principal principal) {
        return principal == null ? "system" : principal.getName();
    }
}
