package id.pdam.sia.admin.web;

import id.pdam.sia.admin.application.IdentityProviderAdminPort;
import id.pdam.sia.admin.application.UserAdministrationService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserAdminResponse(
        UUID id,
        String username,
        String email,
        boolean enabled,
        List<String> roles,
        List<String> authorities,
        IdentityProviderAdminPort.IdentityProviderStatus identityProviderStatus,
        Instant updatedAt
) {
    public static UserAdminResponse from(UserAdministrationService.AdminUser user) {
        return new UserAdminResponse(
                user.id(),
                user.username(),
                user.email(),
                user.enabled(),
                user.roles(),
                user.authorities(),
                user.identityProviderStatus(),
                user.updatedAt()
        );
    }
}
