package id.pdam.sia.admin.domain;

import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_settings")
public class AppSetting extends BaseEntity {
    @Column(nullable = false, unique = true, length = 128)
    private String settingKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String settingValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AppSettingType valueType;

    @Column(length = 255)
    private String description;

    @Column(nullable = false, length = 128)
    private String updatedBy;

    protected AppSetting() {
    }

    public AppSetting(String settingKey, String settingValue, AppSettingType valueType, String description, String updatedBy) {
        this.settingKey = require(settingKey, "SETTING_KEY_REQUIRED", "Setting key is required.");
        update(settingValue, valueType, description, updatedBy);
    }

    public void update(String settingValue, AppSettingType valueType, String description, String updatedBy) {
        this.settingValue = require(settingValue, "SETTING_VALUE_REQUIRED", "Setting value is required.");
        if (valueType == null) {
            throw new BusinessException("SETTING_TYPE_REQUIRED", "Setting value type is required.");
        }
        this.valueType = valueType;
        this.description = normalize(description);
        this.updatedBy = require(updatedBy, "SETTING_UPDATED_BY_REQUIRED", "Setting update actor is required.");
    }

    private static String require(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public String getSettingKey() {
        return settingKey;
    }

    public String getSettingValue() {
        return settingValue;
    }

    public AppSettingType getValueType() {
        return valueType;
    }

    public String getDescription() {
        return description;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }
}
