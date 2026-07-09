package id.pdam.sia.shared.security;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentReconciliationHandoffAcknowledgementMigrationTest {
    @Test
    void handoffAcknowledgementMigrationCreatesImmutablePacketAcknowledgementTable() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V18__payment_reconciliation_handoff_acknowledgements.sql"
        ));

        assertThat(sql)
                .contains("CREATE TABLE payment_reconciliation_handoff_acknowledgements")
                .contains("packet_scope_hash VARCHAR(128) NOT NULL UNIQUE")
                .contains("filter_snapshot TEXT NOT NULL")
                .contains("stale_note_count BIGINT NOT NULL")
                .contains("owner_count BIGINT NOT NULL")
                .contains("max_overdue_days BIGINT NOT NULL")
                .contains("acknowledged_by VARCHAR(128) NOT NULL")
                .contains("acknowledged_at TIMESTAMPTZ NOT NULL")
                .contains("chk_payment_reconciliation_handoff_ack_counts")
                .contains("chk_payment_reconciliation_handoff_ack_reason")
                .contains("idx_payment_reconciliation_handoff_ack_at")
                .contains("idx_payment_reconciliation_handoff_ack_actor");
    }

    @Test
    void permissionSeedLimitsStaleAcknowledgementToSupervisorRoles() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V19__payment_reconciliation_stale_acknowledgement_permission_seed.sql"
        ));

        assertThat(sql)
                .contains("'payment.reconciliation.stale-acknowledge'")
                .contains("('super-admin', 'payment.reconciliation.stale-acknowledge')")
                .contains("('finance-supervisor', 'payment.reconciliation.stale-acknowledge')")
                .doesNotContain("('auditor-internal', 'payment.reconciliation.stale-acknowledge')")
                .doesNotContain("('kasir', 'payment.reconciliation.stale-acknowledge')");
    }
}
