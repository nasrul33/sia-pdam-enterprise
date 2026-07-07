package id.pdam.sia.shared.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentReconciliationSessionMigrationTest {
    private static final Path MIGRATION_PATH = Path.of(
            "src/main/resources/db/migration/V12__payment_reconciliation_sessions.sql"
    );

    @Test
    void createsSessionAndItemTablesWithOperationalStatuses() throws IOException {
        String migration = migrationSql();

        assertThat(migration)
                .contains("CREATE TABLE payment_reconciliation_sessions")
                .contains("CREATE TABLE payment_reconciliation_items")
                .contains("status IN ('OPEN','COMPLETED','CANCELLED')")
                .contains("match_status IN ('EXACT_MATCH','PROBABLE_MATCH','AMOUNT_VARIANCE','REVERSED_PAYMENT','MULTIPLE_CANDIDATES','UNMATCHED')")
                .contains("resolution_status IN ('OPEN','ACCEPTED','RESOLVED','IGNORED')");
    }

    @Test
    void addsIndexesForSessionReviewAndResolutionWorkflows() throws IOException {
        String migration = migrationSql();

        assertThat(migration)
                .contains("idx_payment_reconciliation_sessions_status_started")
                .contains("idx_payment_reconciliation_items_session")
                .contains("idx_payment_reconciliation_items_resolution")
                .contains("idx_payment_reconciliation_items_match_status")
                .contains("idx_payment_reconciliation_items_matched_payment");
    }

    @Test
    void keepsReconciliationSessionNonPostingAndNonDestructive() throws IOException {
        String migration = migrationSql().toLowerCase();

        assertThat(migration)
                .doesNotContain("on delete cascade")
                .doesNotContain("references journal_entries")
                .doesNotContain("references ledger_entries");
    }

    private static String migrationSql() throws IOException {
        assertThat(MIGRATION_PATH).exists();
        return Files.readString(MIGRATION_PATH);
    }
}
