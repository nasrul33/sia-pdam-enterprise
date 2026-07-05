package id.pdam.sia.accounting.domain;

import id.pdam.sia.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JournalEntryTest {

    @Test
    void postsBalancedJournalInOpenPeriod() {
        AccountingPeriod period = new AccountingPeriod("2026-07");
        JournalEntry journal = JournalEntry.draft("JV-2026-07-0001", period.getId(), "Test posting");
        journal.addLine(UUID.randomUUID(), new BigDecimal("1000.00"), BigDecimal.ZERO, "Debit cash");
        journal.addLine(UUID.randomUUID(), BigDecimal.ZERO, new BigDecimal("1000.00"), "Credit revenue");

        journal.post(period, "tester");

        assertThat(journal.getStatus()).isEqualTo(JournalStatus.POSTED);
    }

    @Test
    void rejectsUnbalancedJournal() {
        AccountingPeriod period = new AccountingPeriod("2026-07");
        JournalEntry journal = JournalEntry.draft("JV-2026-07-0002", period.getId(), "Test posting");
        journal.addLine(UUID.randomUUID(), new BigDecimal("1000.00"), BigDecimal.ZERO, "Debit cash");
        journal.addLine(UUID.randomUUID(), BigDecimal.ZERO, new BigDecimal("900.00"), "Credit revenue");

        assertThatThrownBy(() -> journal.post(period, "tester"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Debit must equal credit");
    }

    @Test
    void rejectsMutationAfterPosted() {
        AccountingPeriod period = new AccountingPeriod("2026-07");
        JournalEntry journal = JournalEntry.draft("JV-2026-07-0003", period.getId(), "Test posting");
        journal.addLine(UUID.randomUUID(), new BigDecimal("1000.00"), BigDecimal.ZERO, "Debit cash");
        journal.addLine(UUID.randomUUID(), BigDecimal.ZERO, new BigDecimal("1000.00"), "Credit revenue");
        journal.post(period, "tester");

        assertThatThrownBy(() -> journal.addLine(UUID.randomUUID(), BigDecimal.ONE, BigDecimal.ZERO, "Late edit"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only draft journal can be changed");
    }
}
