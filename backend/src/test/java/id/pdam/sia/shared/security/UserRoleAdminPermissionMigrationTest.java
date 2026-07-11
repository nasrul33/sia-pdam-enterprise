package id.pdam.sia.shared.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class UserRoleAdminPermissionMigrationTest {
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V27__user_role_admin_permissions.sql"
    );

    @Test
    void seedsGranularUserAdministrationPermissionsAndControlledGrants() throws IOException {
        assertThat(MIGRATION).exists();
        String sql = Files.readString(MIGRATION);

        assertThat(sql)
                .contains("'user.read'", "'user.manage'", "'role.manage'")
                .contains("('super-admin', 'user.read')")
                .contains("('super-admin', 'user.manage')")
                .contains("('super-admin', 'role.manage')")
                .contains("('admin-sistem', 'user.read')")
                .contains("('admin-sistem', 'user.manage')")
                .contains("('admin-sistem', 'role.manage')")
                .contains("('auditor-internal', 'user.read')")
                .doesNotContain("('auditor-internal', 'user.manage')")
                .doesNotContain("('auditor-internal', 'role.manage')")
                .doesNotContain("DROP ", "TRUNCATE ", "DELETE ");
    }
}
