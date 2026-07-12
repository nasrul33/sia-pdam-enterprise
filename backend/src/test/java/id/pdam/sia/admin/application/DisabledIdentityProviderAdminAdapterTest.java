package id.pdam.sia.admin.application;

import id.pdam.sia.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisabledIdentityProviderAdminAdapterTest {
    @Test
    void localProfileAllowsDatabaseAuthoritativeMutations() {
        DisabledIdentityProviderAdminAdapter adapter = new DisabledIdentityProviderAdminAdapter(true);

        assertThat(adapter.status(UUID.randomUUID(), "admin"))
                .isEqualTo(IdentityProviderAdminPort.IdentityProviderStatus.LOCAL_ONLY);
        assertThatCode(() -> adapter.updateEnabled("admin", true)).doesNotThrowAnyException();
        assertThatCode(() -> adapter.replaceRoles("admin", Set.of("super-admin"))).doesNotThrowAnyException();
    }

    @Test
    void productionBoundaryRejectsLocalMutations() {
        DisabledIdentityProviderAdminAdapter adapter = new DisabledIdentityProviderAdminAdapter(false);

        assertThat(adapter.status(UUID.randomUUID(), "admin"))
                .isEqualTo(IdentityProviderAdminPort.IdentityProviderStatus.EXTERNAL_MANAGED);
        assertThatThrownBy(() -> adapter.updateEnabled("admin", false))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("IDP_ADMIN_MUTATION_EXTERNAL");
        assertThatThrownBy(() -> adapter.replaceRoles("admin", Set.of("finance-supervisor")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("IDP_ADMIN_MUTATION_EXTERNAL");
    }
}
