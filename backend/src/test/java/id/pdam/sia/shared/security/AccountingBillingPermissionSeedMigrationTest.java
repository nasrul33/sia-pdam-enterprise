package id.pdam.sia.shared.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AccountingBillingPermissionSeedMigrationTest {
    private static final Path MIGRATION_PATH = Path.of(
            "src/main/resources/db/migration/V9__accounting_billing_permission_seed.sql"
    );

    @Test
    void seedsAccountingAndBillingCommandPermissions() throws IOException {
        String migration = migrationSql();

        assertThat(migration)
                .contains("'account.manage'")
                .contains("'period.manage'")
                .contains("'period.close'")
                .contains("'journal.create'")
                .contains("'journal.post'")
                .contains("'billing.generate'")
                .contains("'invoice.issue'");
    }

    @Test
    void mapsAccountingAndBillingPermissionsToExpectedRoles() throws IOException {
        String migration = migrationSql();

        assertThat(migration)
                .contains("('super-admin', 'account.manage')")
                .contains("('super-admin', 'period.manage')")
                .contains("('super-admin', 'period.close')")
                .contains("('super-admin', 'journal.create')")
                .contains("('super-admin', 'journal.post')")
                .contains("('super-admin', 'billing.generate')")
                .contains("('super-admin', 'invoice.issue')")
                .contains("('finance-staff', 'journal.create')")
                .contains("('finance-supervisor', 'account.manage')")
                .contains("('finance-supervisor', 'period.manage')")
                .contains("('finance-supervisor', 'period.close')")
                .contains("('finance-supervisor', 'journal.create')")
                .contains("('finance-supervisor', 'journal.post')")
                .contains("('billing-officer', 'billing.generate')")
                .contains("('billing-supervisor', 'billing.generate')")
                .contains("('billing-supervisor', 'invoice.issue')");
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
