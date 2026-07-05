package id.pdam.sia.metering.domain;

import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "meter_routes")
public class MeterRoute extends BaseEntity {
    @Column(nullable = false, unique = true, length = 64)
    private String routeCode;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 64)
    private String areaCode;

    protected MeterRoute() {
    }

    public MeterRoute(String routeCode, String name, String areaCode) {
        this.routeCode = require(routeCode, "METER_ROUTE_CODE_REQUIRED", "Meter route code is required.");
        this.name = require(name, "METER_ROUTE_NAME_REQUIRED", "Meter route name is required.");
        this.areaCode = require(areaCode, "METER_ROUTE_AREA_REQUIRED", "Meter route area code is required.");
    }

    private static String require(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    public String getRouteCode() {
        return routeCode;
    }

    public String getName() {
        return name;
    }

    public String getAreaCode() {
        return areaCode;
    }
}
