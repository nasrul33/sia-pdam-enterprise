package id.pdam.sia.shared.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentReconciliationPermissionSeedMigrationTest {
    private static final Path MIGRATION_PATH = Path.of(
            "src/main/resources/db/migration/V11__payment_reconciliation_permission_seed.sql"
    );

    @Test
    void seedsPaymentReconciliationPermissionForSupervisoryRolesOnly() throws IOException {
        String migration = migrationSql();

        assertThat(migration)
                .contains("'payment.reconcile'")
                .contains("('super-admin', 'payment.reconcile')")
                .contains("('finance-supervisor', 'payment.reconcile')")
                .contains("('auditor-internal', 'payment.reconcile')")
                .doesNotContain("('kasir', 'payment.reconcile')");
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
