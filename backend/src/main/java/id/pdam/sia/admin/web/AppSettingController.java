package id.pdam.sia.admin.web;

import id.pdam.sia.admin.application.AppSettingApplicationService;
import id.pdam.sia.admin.domain.AppSetting;
import id.pdam.sia.admin.domain.AppSettingType;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/admin/settings")
public class AppSettingController {
    private final AppSettingApplicationService service;

    public AppSettingController(AppSettingApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Permissions.SETTING_MANAGE)
    public PageResponse<AppSettingResponse> listSettings(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size
    ) {
        return PageResponse.from(service.listSettings(page, size).map(AppSettingResponse::from));
    }

    @PostMapping
    @PreAuthorize(Permissions.SETTING_MANAGE)
    public AppSettingResponse upsert(@Valid @RequestBody UpsertSettingRequest request, Principal principal) {
        return AppSettingResponse.from(service.upsert(request.toCommand(), principal == null ? "system" : principal.getName()));
    }

    public record UpsertSettingRequest(
            @NotBlank String settingKey,
            @NotBlank String settingValue,
            @NotNull AppSettingType valueType,
            String description,
            @NotBlank String reason
    ) {
        private AppSettingApplicationService.UpsertSettingCommand toCommand() {
            return new AppSettingApplicationService.UpsertSettingCommand(settingKey, settingValue, valueType, description, reason);
        }
    }

    public record AppSettingResponse(
            UUID id,
            String settingKey,
            String settingValue,
            AppSettingType valueType,
            String description,
            String updatedBy,
            Instant createdAt,
            Instant updatedAt
    ) {
        private static AppSettingResponse from(AppSetting setting) {
            return new AppSettingResponse(
                    setting.getId(),
                    setting.getSettingKey(),
                    setting.getSettingValue(),
                    setting.getValueType(),
                    setting.getDescription(),
                    setting.getUpdatedBy(),
                    setting.getCreatedAt(),
                    setting.getUpdatedAt()
            );
        }
    }
}
