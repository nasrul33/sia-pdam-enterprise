package id.pdam.sia.admin.web;

import id.pdam.sia.admin.application.UserAdministrationService;

import java.util.List;
import java.util.UUID;

public record RoleResponse(UUID id, String code, String name, List<String> permissions) {
    public static RoleResponse from(UserAdministrationService.AdminRole role) {
        return new RoleResponse(role.id(), role.code(), role.name(), role.permissions());
    }
}
