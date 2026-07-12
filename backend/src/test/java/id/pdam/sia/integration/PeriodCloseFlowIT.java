package id.pdam.sia.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PeriodCloseFlowIT extends AbstractPostgresIntegrationTest {
    private static final String PERIOD_ID = "00000000-0000-0000-0000-000000000101";

    @Test
    void closingReviewIsBlockedUntilDraftDepreciationAndReconciliationAreCleared() throws Exception {
        mockMvc.perform(get("/api/accounting-periods/{periodId}/pre-close-checklist", PERIOD_ID)
                        .with(user("integration-accountant").authorities(new SimpleGrantedAuthority("period.manage"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closable").value(false))
                .andExpect(jsonPath("$.blockers[*].code", containsInAnyOrder(
                        "DRAFT_JOURNALS",
                        "MISSING_ASSET_DEPRECIATION",
                        "INCOMPLETE_PAYMENT_RECONCILIATIONS",
                        "DRAFT_INVOICES"
                )));

        mockMvc.perform(post("/api/accounting-periods/{periodId}/start-closing-review", PERIOD_ID)
                        .with(user("integration-controller").authorities(new SimpleGrantedAuthority("period.close")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Attempt closing with blockers\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PERIOD_PRE_CLOSE_BLOCKED"));

        clearPreCloseBlockers();

        mockMvc.perform(get("/api/accounting-periods/{periodId}/pre-close-checklist", PERIOD_ID)
                        .with(user("integration-accountant").authorities(new SimpleGrantedAuthority("period.manage"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closable").value(true))
                .andExpect(jsonPath("$.blockers").isEmpty());

        mockMvc.perform(post("/api/accounting-periods/{periodId}/start-closing-review", PERIOD_ID)
                        .with(user("integration-controller").authorities(new SimpleGrantedAuthority("period.close")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"All pre-close controls completed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSING_REVIEW"));
    }

    private void clearPreCloseBlockers() {
        jdbcTemplate.update("""
                UPDATE journal_entries
                SET status = 'POSTED', posted_at = '2026-07-31T16:00:00Z', posted_by = 'integration-accountant'
                WHERE id = '00000000-0000-0000-0000-000000000301'
                """);
        jdbcTemplate.update("""
                INSERT INTO journal_entries (
                    id, journal_number, accounting_period_id, description, status, posted_at, posted_by,
                    source_module, source_record_id, source_document_number
                ) VALUES (
                    '00000000-0000-0000-0000-000000000302', 'INT-DEP-2026-07',
                    '00000000-0000-0000-0000-000000000101', 'Integration depreciation', 'POSTED',
                    '2026-07-31T16:05:00Z', 'integration-accountant', 'FIXED_ASSET_DEPRECIATION',
                    '00000000-0000-0000-0000-000000000a01', 'INT-ASSET-001'
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO fixed_asset_depreciations (
                    id, asset_id, period, amount, journal_entry_id, posted_at, posted_by
                ) VALUES (
                    '00000000-0000-0000-0000-000000000a02',
                    '00000000-0000-0000-0000-000000000a01', '2026-07', 10.00,
                    '00000000-0000-0000-0000-000000000302',
                    '2026-07-31T16:05:00Z', 'integration-accountant'
                )
                """);
        jdbcTemplate.update("""
                UPDATE payment_reconciliation_sessions
                SET status = 'COMPLETED', completed_at = '2026-07-31T16:10:00Z',
                    signed_off_by = 'integration-controller', signed_off_at = '2026-07-31T16:15:00Z',
                    sign_off_reason = 'Integration reconciliation complete'
                WHERE id = '00000000-0000-0000-0000-000000000901'
                """);
        jdbcTemplate.update("DELETE FROM invoices WHERE id = ?", java.util.UUID.fromString(
                "00000000-0000-0000-0000-000000000801"
        ));
    }
}
