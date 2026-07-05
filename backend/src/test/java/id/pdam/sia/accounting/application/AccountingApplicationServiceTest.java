package id.pdam.sia.accounting.application;

import id.pdam.sia.accounting.domain.Account;
import id.pdam.sia.accounting.domain.AccountType;
import id.pdam.sia.accounting.domain.AccountingPeriod;
import id.pdam.sia.accounting.domain.JournalEntry;
import id.pdam.sia.accounting.repository.AccountRepository;
import id.pdam.sia.accounting.repository.AccountingPeriodRepository;
import id.pdam.sia.accounting.repository.JournalEntryRepository;
import id.pdam.sia.accounting.web.CreateAccountRequest;
import id.pdam.sia.accounting.web.CreateAccountingPeriodRequest;
import id.pdam.sia.accounting.web.CreateJournalRequest;
import id.pdam.sia.accounting.web.JournalLineRequest;
import id.pdam.sia.reporting.application.LedgerEntryMaterializationService;
import id.pdam.sia.shared.audit.AuditTrailService;
import id.pdam.sia.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountingApplicationServiceTest {
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final AccountingPeriodRepository accountingPeriodRepository = mock(AccountingPeriodRepository.class);
    private final JournalEntryRepository journalEntryRepository = mock(JournalEntryRepository.class);
    private final AuditTrailService auditTrailService = mock(AuditTrailService.class);
    private final LedgerEntryMaterializationService ledgerEntryMaterializationService = mock(LedgerEntryMaterializationService.class);
    private final PostingService postingService = new PostingService(auditTrailService, ledgerEntryMaterializationService);

    private final AccountingApplicationService service = new AccountingApplicationService(
            accountRepository,
            accountingPeriodRepository,
            journalEntryRepository,
            postingService,
            auditTrailService
    );

    @Test
    void createsAccountAndWritesAuditTrail() {
        when(accountRepository.findByCode("1-100")).thenReturn(Optional.empty());
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Account account = service.createAccount(
                new CreateAccountRequest(" 1-100 ", "Kas Loket", AccountType.ASSET, "setup CoA"),
                "finance.admin"
        );

        assertThat(account.getCode()).isEqualTo("1-100");
        assertThat(account.getNormalBalance().name()).isEqualTo("DEBIT");
        verify(auditTrailService).record("finance.admin", "ACCOUNTING", "CREATE_ACCOUNT", account.getId().toString(), "setup CoA");
    }

    @Test
    void rejectsDuplicateAccountCode() {
        when(accountRepository.findByCode("1-100"))
                .thenReturn(Optional.of(new Account("1-100", "Kas Loket", AccountType.ASSET)));

        assertThatThrownBy(() -> service.createAccount(
                new CreateAccountRequest("1-100", "Kas Loket", AccountType.ASSET, "setup CoA"),
                "finance.admin"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Account code already exists");
    }

    @Test
    void createsDraftJournalAfterValidatingPeriodAndAccounts() {
        AccountingPeriod period = new AccountingPeriod("2026-07");
        UUID cashAccountId = UUID.randomUUID();
        UUID revenueAccountId = UUID.randomUUID();

        when(journalEntryRepository.findByJournalNumber("JV-2026-07-0001")).thenReturn(Optional.empty());
        when(accountingPeriodRepository.findById(period.getId())).thenReturn(Optional.of(period));
        when(accountRepository.existsById(cashAccountId)).thenReturn(true);
        when(accountRepository.existsById(revenueAccountId)).thenReturn(true);
        when(journalEntryRepository.save(any(JournalEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JournalEntry journal = service.createJournal(
                new CreateJournalRequest(
                        "JV-2026-07-0001",
                        period.getId(),
                        "Tagihan air periode Juli 2026",
                        List.of(
                                new JournalLineRequest(cashAccountId, new BigDecimal("1000.00"), BigDecimal.ZERO, "Debit kas"),
                                new JournalLineRequest(revenueAccountId, BigDecimal.ZERO, new BigDecimal("1000.00"), "Kredit pendapatan")
                        ),
                        "draft manual"
                ),
                "finance.staff"
        );

        assertThat(journal.isBalanced()).isTrue();
        assertThat(journal.getLines()).hasSize(2);
        verify(auditTrailService).record(
                "finance.staff",
                "ACCOUNTING",
                "CREATE_DRAFT_JOURNAL",
                journal.getId().toString(),
                "draft manual"
        );
    }

    @Test
    void rejectsDraftJournalWhenAccountDoesNotExist() {
        AccountingPeriod period = new AccountingPeriod("2026-07");
        UUID missingAccountId = UUID.randomUUID();
        UUID revenueAccountId = UUID.randomUUID();

        when(journalEntryRepository.findByJournalNumber("JV-2026-07-0001")).thenReturn(Optional.empty());
        when(accountingPeriodRepository.findById(period.getId())).thenReturn(Optional.of(period));
        when(accountRepository.existsById(missingAccountId)).thenReturn(false);
        when(accountRepository.existsById(revenueAccountId)).thenReturn(true);

        assertThatThrownBy(() -> service.createJournal(
                new CreateJournalRequest(
                        "JV-2026-07-0001",
                        period.getId(),
                        "Tagihan air periode Juli 2026",
                        List.of(
                                new JournalLineRequest(missingAccountId, new BigDecimal("1000.00"), BigDecimal.ZERO, "Debit piutang"),
                                new JournalLineRequest(revenueAccountId, BigDecimal.ZERO, new BigDecimal("1000.00"), "Kredit pendapatan")
                        ),
                        "draft manual"
                ),
                "finance.staff"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Journal account was not found");
    }

    @Test
    void blocksPostingWhenPeriodIsLocked() {
        AccountingPeriod period = new AccountingPeriod("2026-07");
        period.startClosingReview();
        period.lock();
        JournalEntry journal = JournalEntry.draft("JV-2026-07-0001", period.getId(), "Locked period posting");
        journal.addLine(UUID.randomUUID(), new BigDecimal("1000.00"), BigDecimal.ZERO, "Debit kas");
        journal.addLine(UUID.randomUUID(), BigDecimal.ZERO, new BigDecimal("1000.00"), "Kredit pendapatan");

        when(journalEntryRepository.findForPosting(journal.getId())).thenReturn(Optional.of(journal));
        when(accountingPeriodRepository.findById(period.getId())).thenReturn(Optional.of(period));

        assertThatThrownBy(() -> service.postJournal(journal.getId(), "posting test", "finance.supervisor"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Posting is not allowed in this period");
    }

    @Test
    void createsAccountingPeriodAndWritesAuditTrail() {
        when(accountingPeriodRepository.findByPeriod("2026-07")).thenReturn(Optional.empty());
        when(accountingPeriodRepository.save(any(AccountingPeriod.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountingPeriod period = service.createAccountingPeriod(
                new CreateAccountingPeriodRequest("2026-07", "open July period"),
                "finance.admin"
        );

        assertThat(period.getPeriod()).isEqualTo("2026-07");
        verify(auditTrailService).record(
                "finance.admin",
                "ACCOUNTING",
                "CREATE_ACCOUNTING_PERIOD",
                period.getId().toString(),
                "open July period"
        );
    }
}
