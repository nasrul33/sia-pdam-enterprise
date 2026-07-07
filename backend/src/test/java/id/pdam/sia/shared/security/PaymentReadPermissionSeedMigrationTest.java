package id.pdam.sia.shared.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentReadPermissionSeedMigrationTest {
    private static final Path MIGRATION_PATH = Path.of(
            "src/main/resources/db/migration/V10__payment_read_permission_seed.sql"
    );

    @Test
    void seedsPaymentReadPermissionForSupervisoryRolesOnly() throws IOException {
        String migration = migrationSql();

        assertThat(migration)
                .contains("'payment.read'")
                .contains("('super-admin', 'payment.read')")
                .contains("('finance-supervisor', 'payment.read')")
                .contains("('auditor-internal', 'payment.read')")
                .doesNotContain("('kasir', 'payment.read')");
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
