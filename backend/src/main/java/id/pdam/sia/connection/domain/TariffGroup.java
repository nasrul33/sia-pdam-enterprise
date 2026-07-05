package id.pdam.sia.connection.domain;

import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "tariff_groups")
public class TariffGroup extends BaseEntity {
    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    protected TariffGroup() {
    }

    public TariffGroup(String code, String name) {
        this.code = require(code, "TARIFF_GROUP_CODE_REQUIRED", "Tariff group code is required.");
        this.name = require(name, "TARIFF_GROUP_NAME_REQUIRED", "Tariff group name is required.");
    }

    private static String require(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}
