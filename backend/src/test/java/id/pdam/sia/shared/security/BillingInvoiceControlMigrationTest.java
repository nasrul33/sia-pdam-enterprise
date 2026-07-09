package id.pdam.sia.shared.security;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BillingInvoiceControlMigrationTest {
    @Test
    void invoiceVoidTraceMigrationAddsJournalTraceAndConstraint() throws Exception {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V20__billing_invoice_void_trace.sql"));

        assertThat(migration)
                .contains("void_journal_entry_id UUID REFERENCES journal_entries(id)")
                .contains("voided_at TIMESTAMPTZ")
                .contains("uq_invoices_void_journal_entry")
                .contains("chk_invoices_void_trace")
                .contains("status <> 'VOID'");
    }

    @Test
    void invoiceControlPermissionSeedLimitsCorrectionToSupervisorRoles() throws Exception {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V21__billing_invoice_view_correct_permission_seed.sql"));

        assertThat(migration)
                .contains("'invoice.view'")
                .contains("'invoice.correct.approve'")
                .contains("('super-admin', 'invoice.correct.approve')")
                .contains("('finance-supervisor', 'invoice.correct.approve')")
                .contains("('billing-supervisor', 'invoice.correct.approve')")
                .contains("('auditor', 'invoice.view')");
        assertThat(migration)
                .doesNotContain("('auditor', 'invoice.correct.approve')")
                .doesNotContain("('billing-officer', 'invoice.correct.approve')")
                .doesNotContain("('cashier', 'invoice.correct.approve')");
    }
}
