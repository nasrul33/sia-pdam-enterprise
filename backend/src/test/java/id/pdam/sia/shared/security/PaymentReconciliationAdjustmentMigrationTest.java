package id.pdam.sia.shared.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentReconciliationAdjustmentMigrationTest {
    private static final Path MIGRATION_PATH = Path.of(
            "src/main/resources/db/migration/V13__payment_reconciliation_adjustment_trace.sql"
    );

    @Test
    void addsAdjustmentJournalTraceColumnsWithoutDestructiveChanges() throws IOException {
        String migration = migrationSql();

        assertThat(migration)
                .contains("adjustment_journal_entry_id UUID")
                .contains("adjustment_reason TEXT")
                .contains("adjusted_by VARCHAR(128)")
                .contains("adjusted_at TIMESTAMPTZ")
                .doesNotContain("DROP TABLE")
                .doesNotContain("DROP COLUMN");
    }

    @Test
    void addsUniqueJournalTraceAndReviewIndex() throws IOException {
        String migration = migrationSql();

        assertThat(migration)
                .contains("uq_payment_reconciliation_items_adjustment_journal")
                .contains("idx_payment_reconciliation_items_adjusted_at")
                .contains("WHERE adjustment_journal_entry_id IS NOT NULL");
    }

    private static String migrationSql() throws IOException {
        assertThat(MIGRATION_PATH).exists();
        return Files.readString(MIGRATION_PATH);
    }
}
