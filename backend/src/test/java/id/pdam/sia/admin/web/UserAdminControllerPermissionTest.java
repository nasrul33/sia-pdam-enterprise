package id.pdam.sia.admin.web;

import id.pdam.sia.shared.security.Permissions;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class UserAdminControllerPermissionTest {
    @Test
    void exposesGranularUserAndRolePermissions() {
        assertThat(Permissions.USER_READ).isEqualTo("hasAuthority('user.read')");
        assertThat(Permissions.USER_MANAGE).isEqualTo("hasAuthority('user.manage')");
        assertThat(Permissions.ROLE_MANAGE).isEqualTo("hasAuthority('role.manage')");

        assertPermission("listUsers", Permissions.USER_READ);
        assertPermission("listRoles", Permissions.USER_READ);
        assertPermission("updateStatus", Permissions.USER_MANAGE);
        assertPermission("replaceRoles", Permissions.ROLE_MANAGE);
    }

    private static void assertPermission(String methodName, String permission) {
        Method method = Arrays.stream(UserAdminController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(permission);
    }
}
