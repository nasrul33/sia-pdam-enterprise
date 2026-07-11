package id.pdam.sia.admin.application;

import java.util.Set;
import java.util.UUID;

public interface IdentityProviderAdminPort {
    IdentityProviderStatus status(UUID userId, String username);

    void updateEnabled(String username, boolean enabled);

    void replaceRoles(String username, Set<String> roleCodes);

    enum IdentityProviderStatus {
        LOCAL_ONLY,
        SYNCED,
        SYNC_ERROR
    }
}
