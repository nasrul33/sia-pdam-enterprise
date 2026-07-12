package id.pdam.sia.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BillingPaymentAccountingFlowIT extends AbstractPostgresIntegrationTest {
    private static final String INVOICE_ID = "00000000-0000-0000-0000-000000000801";
    private static final String RECEIVABLE_ACCOUNT_ID = "00000000-0000-0000-0000-000000000201";
    private static final String CASH_ACCOUNT_ID = "00000000-0000-0000-0000-000000000202";
    private static final String REVENUE_ACCOUNT_ID = "00000000-0000-0000-0000-000000000203";

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void issueSettleReverseAndVoidRemainBalancedAndSourceTraceable() throws Exception {
        mockMvc.perform(post("/api/invoices/{invoiceId}/issue", INVOICE_ID)
                        .with(user("integration-billing").authorities(new SimpleGrantedAuthority("invoice.issue")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "receivableAccountId": "%s",
                                  "revenueAccountId": "%s",
                                  "reason": "Integration issue approval"
                                }
                                """.formatted(RECEIVABLE_ACCOUNT_ID, REVENUE_ACCOUNT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ISSUED"));

        MvcResult settlement = mockMvc.perform(post("/api/payments/counter")
                        .with(user("integration-cashier").authorities(new SimpleGrantedAuthority("payment.counter")))
                        .header("Idempotency-Key", "integration-payment-2026-07")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "externalReference": "COUNTER-INTEGRATION-001",
                                  "amount": 100.00,
                                  "paidAt": "2026-07-15T03:00:00Z",
                                  "allocations": [{"invoiceId": "%s", "amount": 100.00}],
                                  "cashAccountId": "%s",
                                  "receivableAccountId": "%s",
                                  "reason": "Integration counter settlement"
                                }
                                """.formatted(INVOICE_ID, CASH_ACCOUNT_ID, RECEIVABLE_ACCOUNT_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andReturn();

        JsonNode settlementBody = objectMapper.readTree(settlement.getResponse().getContentAsString());
        String paymentId = settlementBody.required("id").asText();

        mockMvc.perform(post("/api/payments/{paymentId}/reverse", paymentId)
                        .with(user("integration-supervisor").authorities(new SimpleGrantedAuthority("payment.reverse")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cashAccountId": "%s",
                                  "receivableAccountId": "%s",
                                  "reason": "Integration settlement reversal"
                                }
                                """.formatted(CASH_ACCOUNT_ID, RECEIVABLE_ACCOUNT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVERSED"));

        mockMvc.perform(post("/api/invoices/{invoiceId}/void", INVOICE_ID)
                        .with(user("integration-controller").authorities(
                                new SimpleGrantedAuthority("invoice.correct.approve")
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "Integration invoice cancellation after payment reversal"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOID"));

        Integer journalCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM journal_entries
                WHERE status = 'POSTED'
                  AND source_module IN ('BILLING', 'PAYMENT', 'PAYMENT_REVERSAL', 'BILLING_VOID')
                """, Integer.class);
        BigDecimal journalImbalance = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(ABS(t.debit_total - t.credit_total)), 0)
                FROM (
                    SELECT je.id,
                           COALESCE(SUM(jl.debit), 0) AS debit_total,
                           COALESCE(SUM(jl.credit), 0) AS credit_total
                    FROM journal_entries je
                    JOIN journal_lines jl ON jl.journal_entry_id = je.id
                    WHERE je.status = 'POSTED'
                      AND je.source_module IN ('BILLING', 'PAYMENT', 'PAYMENT_REVERSAL', 'BILLING_VOID')
                    GROUP BY je.id
                ) t
                """, BigDecimal.class);
        BigDecimal ledgerImbalance = jdbcTemplate.queryForObject("""
                SELECT COALESCE(ABS(SUM(debit) - SUM(credit)), 0)
                FROM ledger_entries
                WHERE source_module IN ('BILLING', 'PAYMENT', 'PAYMENT_REVERSAL', 'BILLING_VOID')
                """, BigDecimal.class);

        assertThat(journalCount).isEqualTo(4);
        assertThat(journalImbalance).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(ledgerImbalance).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
