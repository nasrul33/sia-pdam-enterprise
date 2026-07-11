package id.pdam.sia.admin.application;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Component
public class DisabledIdentityProviderAdminAdapter implements IdentityProviderAdminPort {
    @Override
    public IdentityProviderStatus status(UUID userId, String username) {
        return IdentityProviderStatus.LOCAL_ONLY;
    }

    @Override
    public void updateEnabled(String username, boolean enabled) {
        // Local authentication is authoritative until the production IdP adapter is enabled.
    }

    @Override
    public void replaceRoles(String username, Set<String> roleCodes) {
        // Local authentication is authoritative until the production IdP adapter is enabled.
    }
}
