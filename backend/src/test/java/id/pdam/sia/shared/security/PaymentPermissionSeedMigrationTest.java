package id.pdam.sia.shared.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentPermissionSeedMigrationTest {
    private static final Path MIGRATION_PATH = Path.of(
            "src/main/resources/db/migration/V8__payment_permission_seed.sql"
    );

    @Test
    void seedsPaymentPermissions() throws IOException {
        String migration = migrationSql();

        assertThat(migration)
                .contains("'payment.counter'")
                .contains("'payment.reverse'")
                .contains("'payment.webhook.read'");
    }

    @Test
    void mapsPaymentPermissionsToExpectedRoles() throws IOException {
        String migration = migrationSql();

        assertThat(migration)
                .contains("('super-admin', 'payment.counter')")
                .contains("('super-admin', 'payment.reverse')")
                .contains("('super-admin', 'payment.webhook.read')")
                .contains("('kasir', 'payment.counter')")
                .contains("('finance-supervisor', 'payment.reverse')")
                .contains("('finance-supervisor', 'payment.webhook.read')")
                .contains("('auditor-internal', 'payment.webhook.read')");
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
