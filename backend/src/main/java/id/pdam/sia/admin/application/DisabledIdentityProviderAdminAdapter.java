package id.pdam.sia.admin.application;

import id.pdam.sia.shared.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Component
public class DisabledIdentityProviderAdminAdapter implements IdentityProviderAdminPort {
    private final boolean localMutationsEnabled;

    public DisabledIdentityProviderAdminAdapter(
            @Value("${sia.admin.local-mutations-enabled:true}") boolean localMutationsEnabled
    ) {
        this.localMutationsEnabled = localMutationsEnabled;
    }

    @Override
    public IdentityProviderStatus status(UUID userId, String username) {
        return localMutationsEnabled ? IdentityProviderStatus.LOCAL_ONLY : IdentityProviderStatus.EXTERNAL_MANAGED;
    }

    @Override
    public void updateEnabled(String username, boolean enabled) {
        requireLocalMutation();
    }

    @Override
    public void replaceRoles(String username, Set<String> roleCodes) {
        requireLocalMutation();
    }

    private void requireLocalMutation() {
        if (!localMutationsEnabled) {
            throw new BusinessException(
                    "IDP_ADMIN_MUTATION_EXTERNAL",
                    "Production identity status and roles must be managed in the external identity provider."
            );
        }
    }
}
