package id.pdam.sia.shared.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentReconciliationSignOffPermissionSeedMigrationTest {
    private static final Path MIGRATION_PATH = Path.of(
            "src/main/resources/db/migration/V15__payment_reconciliation_signoff_permission_seed.sql"
    );

    @Test
    void seedsSignOffPermissionOnlyForApproverRoles() throws IOException {
        String migration = migrationSql();

        assertThat(migration)
                .contains("'payment.reconciliation.signoff'")
                .contains("('super-admin', 'payment.reconciliation.signoff')")
                .contains("('finance-supervisor', 'payment.reconciliation.signoff')")
                .doesNotContain("('auditor-internal', 'payment.reconciliation.signoff')")
                .doesNotContain("('kasir', 'payment.reconciliation.signoff')");
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
