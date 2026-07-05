package id.pdam.sia.accounting.application;

import id.pdam.sia.accounting.domain.AccountingPeriod;
import id.pdam.sia.accounting.domain.JournalEntry;
import id.pdam.sia.reporting.application.LedgerEntryMaterializationService;
import id.pdam.sia.reporting.domain.LedgerEntry;
import id.pdam.sia.reporting.repository.LedgerEntryRepository;
import id.pdam.sia.shared.audit.AuditTrailService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LedgerMaterializationTest {
    private final LedgerEntryRepository ledgerEntryRepository = mock(LedgerEntryRepository.class);
    private final LedgerEntryMaterializationService materializationService = new LedgerEntryMaterializationService(
            ledgerEntryRepository
    );
    private final AuditTrailService auditTrailService = mock(AuditTrailService.class);
    private final PostingService postingService = new PostingService(auditTrailService, materializationService);

    @Test
    void postingBalancedJournalMaterializesPostedLedgerEntries() {
        AccountingPeriod period = new AccountingPeriod("2026-07");
        UUID cashAccountId = UUID.randomUUID();
        UUID revenueAccountId = UUID.randomUUID();
        JournalEntry journal = JournalEntry.draft("JV-2026-07-0001", period.getId(), "Posting kas");
        journal.addLine(cashAccountId, new BigDecimal("150000.00"), BigDecimal.ZERO, "Debit kas");
        journal.addLine(revenueAccountId, BigDecimal.ZERO, new BigDecimal("150000.00"), "Kredit pendapatan");
        when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        postingService.post(journal, period, "finance.supervisor", "posting approved");

        ArgumentCaptor<LedgerEntry> ledgerCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, times(2)).save(ledgerCaptor.capture());
        assertThat(ledgerCaptor.getAllValues()).hasSize(2);
        assertThat(ledgerCaptor.getAllValues()).extracting(LedgerEntry::getJournalEntryId)
                .containsOnly(journal.getId());
        assertThat(ledgerCaptor.getAllValues()).extracting(LedgerEntry::getAccountId)
                .containsExactly(cashAccountId, revenueAccountId);
        assertThat(ledgerCaptor.getAllValues().getFirst().getDebit()).isEqualByComparingTo("150000.00");
        assertThat(ledgerCaptor.getAllValues().getFirst().getCredit()).isEqualByComparingTo("0.00");
        assertThat(ledgerCaptor.getAllValues().getLast().getDebit()).isEqualByComparingTo("0.00");
        assertThat(ledgerCaptor.getAllValues().getLast().getCredit()).isEqualByComparingTo("150000.00");
        assertThat(ledgerCaptor.getAllValues()).extracting(LedgerEntry::getSourceModule).containsOnly("ACCOUNTING");
        assertThat(ledgerCaptor.getAllValues()).extracting(LedgerEntry::getSourceRecordId).containsOnly(journal.getId());
        assertThat(ledgerCaptor.getAllValues()).extracting(LedgerEntry::getPostingDate)
                .containsOnly(LocalDate.ofInstant(journal.getPostedAt(), ZoneOffset.UTC));
    }
}
