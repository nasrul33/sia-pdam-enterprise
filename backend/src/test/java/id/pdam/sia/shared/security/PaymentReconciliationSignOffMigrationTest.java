package id.pdam.sia.shared.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentReconciliationSignOffMigrationTest {
    private static final Path MIGRATION_PATH = Path.of(
            "src/main/resources/db/migration/V14__payment_reconciliation_sign_off.sql"
    );

    @Test
    void addsSignOffTraceColumnsWithoutDestructiveChanges() throws IOException {
        String migration = migrationSql();

        assertThat(migration)
                .contains("signed_off_by VARCHAR(128)")
                .contains("signed_off_at TIMESTAMPTZ")
                .contains("sign_off_reason TEXT")
                .doesNotContain("DROP TABLE")
                .doesNotContain("DROP COLUMN");
    }

    @Test
    void requiresSignOffTraceToBelongToCompletedSession() throws IOException {
        String migration = migrationSql();

        assertThat(migration)
                .contains("chk_payment_reconciliation_sessions_sign_off_completed")
                .contains("status = 'COMPLETED'")
                .contains("signed_off_by IS NOT NULL")
                .contains("signed_off_at IS NOT NULL")
                .contains("sign_off_reason IS NOT NULL");
    }

    @Test
    void addsReviewIndexForSignedOffEvidence() throws IOException {
        assertThat(migrationSql())
                .contains("idx_payment_reconciliation_sessions_signed_off_at")
                .contains("WHERE signed_off_at IS NOT NULL");
    }

    private static String migrationSql() throws IOException {
        assertThat(MIGRATION_PATH).exists();
        return Files.readString(MIGRATION_PATH);
    }
}
