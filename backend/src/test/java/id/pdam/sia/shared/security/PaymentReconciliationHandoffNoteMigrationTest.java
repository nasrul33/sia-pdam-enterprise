package id.pdam.sia.shared.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentReconciliationHandoffNoteMigrationTest {
    private static final Path SCHEMA_MIGRATION_PATH = Path.of(
            "src/main/resources/db/migration/V16__payment_reconciliation_handoff_notes.sql"
    );
    private static final Path PERMISSION_MIGRATION_PATH = Path.of(
            "src/main/resources/db/migration/V17__payment_reconciliation_handoff_note_permission_seed.sql"
    );

    @Test
    void schemaStoresCurrentNoteAndImmutableRevisionHistory() throws IOException {
        String migration = Files.readString(SCHEMA_MIGRATION_PATH);

        assertThat(migration)
                .contains("CREATE TABLE payment_reconciliation_handoff_notes")
                .contains("CREATE TABLE payment_reconciliation_handoff_note_revisions")
                .contains("REFERENCES payment_reconciliation_sessions(id)")
                .contains("REFERENCES payment_reconciliation_handoff_notes(id)")
                .contains("CONSTRAINT uq_payment_reconciliation_handoff_note_revision UNIQUE (note_id, revision_number)")
                .contains("handoff_status IN ('OPEN','IN_PROGRESS','CLEARED')")
                .contains("idx_payment_reconciliation_handoff_notes_session_updated")
                .contains("idx_payment_reconciliation_handoff_note_revisions_note");
    }

    @Test
    void seedsHandoffNotePermissionForReviewerRolesWithoutDefaultUsers() throws IOException {
        String migration = Files.readString(PERMISSION_MIGRATION_PATH);

        assertThat(migration)
                .contains("'payment.reconciliation.handoff-note'")
                .contains("('super-admin', 'payment.reconciliation.handoff-note')")
                .contains("('finance-supervisor', 'payment.reconciliation.handoff-note')")
                .contains("('auditor-internal', 'payment.reconciliation.handoff-note')")
                .doesNotContain("('kasir', 'payment.reconciliation.handoff-note')")
                .doesNotContain("insert into users")
                .doesNotContain("password_hash");
    }
}
