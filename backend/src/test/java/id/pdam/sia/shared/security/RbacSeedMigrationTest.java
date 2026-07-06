package id.pdam.sia.shared.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RbacSeedMigrationTest {
    private static final Path MIGRATION_PATH = Path.of(
            "src/main/resources/db/migration/V7__rbac_seed.sql"
    );

    @Test
    void seedsCollectionActionPermissionsAndOperationalRoles() throws IOException {
        String migration = migrationSql();

        assertThat(migration)
                .contains("'collection-action.read'")
                .contains("'collection-action.create'")
                .contains("'collection-action.execute'")
                .contains("'collection-action.cancel'")
                .contains("'super-admin'")
                .contains("'petugas-piutang'")
                .contains("'supervisor-piutang'")
                .contains("'auditor-internal'");
    }

    @Test
    void mapsCollectionActionPermissionsToExpectedRoles() throws IOException {
        String migration = migrationSql();

        assertThat(migration)
                .contains("('super-admin', 'collection-action.read')")
                .contains("('super-admin', 'collection-action.create')")
                .contains("('super-admin', 'collection-action.execute')")
                .contains("('super-admin', 'collection-action.cancel')")
                .contains("('petugas-piutang', 'collection-action.read')")
                .contains("('petugas-piutang', 'collection-action.create')")
                .contains("('petugas-piutang', 'collection-action.execute')")
                .contains("('supervisor-piutang', 'collection-action.read')")
                .contains("('supervisor-piutang', 'collection-action.create')")
                .contains("('supervisor-piutang', 'collection-action.cancel')")
                .contains("('auditor-internal', 'collection-action.read')");
    }

    @Test
    void doesNotSeedDefaultUsersOrPasswords() throws IOException {
        String migration = migrationSql().toLowerCase();

        assertThat(migration)
                .doesNotContain("insert into users")
                .doesNotContain("password_hash")
                .doesNotContain("{noop}");
    }

    private static String migrationSql() throws IOException {
        assertThat(MIGRATION_PATH).exists();
        return Files.readString(MIGRATION_PATH);
    }
}
