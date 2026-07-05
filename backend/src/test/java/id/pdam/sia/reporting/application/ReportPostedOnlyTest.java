package id.pdam.sia.reporting.application;

import id.pdam.sia.accounting.domain.Account;
import id.pdam.sia.accounting.domain.AccountType;
import id.pdam.sia.accounting.repository.AccountRepository;
import id.pdam.sia.reporting.domain.LedgerEntry;
import id.pdam.sia.reporting.repository.LedgerEntryRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportPostedOnlyTest {
    private final LedgerEntryRepository ledgerEntryRepository = mock(LedgerEntryRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final PostedLedgerReportApplicationService service = new PostedLedgerReportApplicationService(
            ledgerEntryRepository,
            accountRepository
    );

    @Test
    void trialBalanceUsesPostedLedgerEntriesOnlyAndBalancesDebitCredit() {
        Account cash = new Account("1110", "Kas", AccountType.ASSET);
        Account revenue = new Account("4110", "Pendapatan Air", AccountType.REVENUE);
        LocalDate fromDate = LocalDate.of(2026, 7, 1);
        LocalDate toDate = LocalDate.of(2026, 7, 31);
        LedgerEntry debitCash = ledgerEntry(cash.getId(), new BigDecimal("100000.00"), BigDecimal.ZERO, toDate);
        LedgerEntry creditRevenue = ledgerEntry(revenue.getId(), BigDecimal.ZERO, new BigDecimal("100000.00"), toDate);

        when(ledgerEntryRepository.findByPostingDateBetween(fromDate, toDate))
                .thenReturn(List.of(debitCash, creditRevenue));
        when(accountRepository.findAllById(List.of(cash.getId(), revenue.getId())))
                .thenReturn(List.of(cash, revenue));

        TrialBalanceReport report = service.trialBalance(fromDate, toDate);

        assertThat(report.fromDate()).isEqualTo(fromDate);
        assertThat(report.toDate()).isEqualTo(toDate);
        assertThat(report.totalDebitBalance()).isEqualByComparingTo("100000.00");
        assertThat(report.totalCreditBalance()).isEqualByComparingTo("100000.00");
        assertThat(report.balanced()).isTrue();
        assertThat(report.lines()).extracting(TrialBalanceLine::accountCode).containsExactly("1110", "4110");
        assertThat(report.lines().getFirst().debitBalance()).isEqualByComparingTo("100000.00");
        assertThat(report.lines().getLast().creditBalance()).isEqualByComparingTo("100000.00");
        verify(ledgerEntryRepository).findByPostingDateBetween(fromDate, toDate);
    }

    private static LedgerEntry ledgerEntry(UUID accountId, BigDecimal debit, BigDecimal credit, LocalDate postingDate) {
        return new LedgerEntry(
                UUID.randomUUID(),
                UUID.randomUUID(),
                accountId,
                postingDate,
                debit,
                credit,
                "ACCOUNTING",
                UUID.randomUUID()
        );
    }
}
