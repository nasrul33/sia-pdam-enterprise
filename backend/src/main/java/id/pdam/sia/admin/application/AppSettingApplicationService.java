package id.pdam.sia.admin.application;

import id.pdam.sia.admin.domain.AppSetting;
import id.pdam.sia.admin.domain.AppSettingType;
import id.pdam.sia.admin.repository.AppSettingRepository;
import id.pdam.sia.shared.audit.AuditTrailService;
import id.pdam.sia.shared.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppSettingApplicationService {
    private final AppSettingRepository appSettingRepository;
    private final AuditTrailService auditTrailService;

    public AppSettingApplicationService(AppSettingRepository appSettingRepository, AuditTrailService auditTrailService) {
        this.appSettingRepository = appSettingRepository;
        this.auditTrailService = auditTrailService;
    }

    @Transactional(readOnly = true)
    public Page<AppSetting> listSettings(int page, int size) {
        return appSettingRepository.findAll(PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("settingKey")));
    }

    @Transactional
    public AppSetting upsert(UpsertSettingCommand command, String actor) {
        String key = requireNormalize(command.settingKey(), "SETTING_KEY_REQUIRED", "Setting key is required.");
        AppSetting setting = appSettingRepository.findBySettingKey(key)
                .map(existing -> {
                    existing.update(command.settingValue(), command.valueType(), command.description(), actor);
                    return existing;
                })
                .orElseGet(() -> new AppSetting(key, command.settingValue(), command.valueType(), command.description(), actor));
        AppSetting saved = appSettingRepository.save(setting);
        auditTrailService.record(actor, "ADMIN", "UPSERT_SETTING", saved.getSettingKey(), command.reason());
        return saved;
    }

    private static String requireNormalize(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    public record UpsertSettingCommand(
            String settingKey,
            String settingValue,
            AppSettingType valueType,
            String description,
            String reason
    ) {
    }
}
