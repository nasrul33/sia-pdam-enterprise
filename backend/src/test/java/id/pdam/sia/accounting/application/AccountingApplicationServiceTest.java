package id.pdam.sia.accounting.application;

import id.pdam.sia.accounting.domain.Account;
import id.pdam.sia.accounting.domain.AccountType;
import id.pdam.sia.accounting.domain.AccountingPeriod;
import id.pdam.sia.accounting.domain.JournalEntry;
import id.pdam.sia.accounting.domain.JournalStatus;
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
import static org.mockito.Mockito.never;
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
    void postsBillingInvoiceJournalWithSourceTraceabilityAndLedgerMaterialization() {
        AccountingPeriod period = new AccountingPeriod("2026-07");
        Account receivableAccount = new Account("1-130", "Piutang Air", AccountType.ASSET);
        Account revenueAccount = new Account("4-110", "Pendapatan Air", AccountType.REVENUE);
        UUID invoiceId = UUID.randomUUID();

        when(accountingPeriodRepository.findByPeriod("2026-07")).thenReturn(Optional.of(period));
        when(accountRepository.findById(receivableAccount.getId())).thenReturn(Optional.of(receivableAccount));
        when(accountRepository.findById(revenueAccount.getId())).thenReturn(Optional.of(revenueAccount));
        when(journalEntryRepository.findByJournalNumber("BIL-INV-202607-SR-0001")).thenReturn(Optional.empty());
        when(journalEntryRepository.save(any(JournalEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JournalEntry journal = service.postBillingInvoice(
                new BillingInvoicePostingCommand(
                        "INV-202607-SR-0001",
                        invoiceId,
                        "2026-07",
                        new BigDecimal("46250.00"),
                        receivableAccount.getId(),
                        revenueAccount.getId(),
                        "issue invoice"
                ),
                "billing.admin"
        );

        assertThat(journal.getStatus()).isEqualTo(JournalStatus.POSTED);
        assertThat(journal.getSourceModule()).isEqualTo("BILLING");
        assertThat(journal.getSourceRecordId()).isEqualTo(invoiceId);
        assertThat(journal.getSourceDocumentNumber()).isEqualTo("INV-202607-SR-0001");
        assertThat(journal.getLines()).hasSize(2);
        assertThat(journal.getLines().get(0).getAccountId()).isEqualTo(receivableAccount.getId());
        assertThat(journal.getLines().get(0).getDebit()).isEqualByComparingTo("46250.00");
        assertThat(journal.getLines().get(1).getAccountId()).isEqualTo(revenueAccount.getId());
        assertThat(journal.getLines().get(1).getCredit()).isEqualByComparingTo("46250.00");
        verify(ledgerEntryMaterializationService).materializePostedJournal(journal);
        verify(auditTrailService).record(
                "billing.admin",
                "ACCOUNTING",
                "POST_JOURNAL",
                journal.getId().toString(),
                "issue invoice"
        );
    }

    @Test
    void postsBillingInvoiceVoidJournalByReversingOriginalPostedBillingLines() {
        AccountingPeriod period = new AccountingPeriod("2026-07");
        UUID receivableAccountId = UUID.randomUUID();
        UUID revenueAccountId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        JournalEntry originalJournal = JournalEntry.draftFromSource(
                "BIL-INV-202607-SR-0001",
                period.getId(),
                "Issue invoice",
                "BILLING",
                invoiceId,
                "INV-202607-SR-0001"
        );
        originalJournal.addLine(receivableAccountId, new BigDecimal("46250.00"), BigDecimal.ZERO, "Piutang tagihan");
        originalJournal.addLine(revenueAccountId, BigDecimal.ZERO, new BigDecimal("46250.00"), "Pendapatan air");
        originalJournal.post(period, "billing.admin");

        when(journalEntryRepository.existsBySourceModuleAndSourceRecordId("BILLING_VOID", invoiceId)).thenReturn(false);
        when(accountingPeriodRepository.findByPeriod("2026-07")).thenReturn(Optional.of(period));
        when(journalEntryRepository.findWithLinesById(originalJournal.getId())).thenReturn(Optional.of(originalJournal));
        when(journalEntryRepository.findByJournalNumber("BIL-VOID-INV-202607-SR-0001")).thenReturn(Optional.empty());
        when(journalEntryRepository.save(any(JournalEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JournalEntry journal = service.postBillingInvoiceVoid(
                new BillingInvoiceVoidPostingCommand(
                        "INV-202607-SR-0001",
                        invoiceId,
                        "2026-07",
                        originalJournal.getId(),
                        "Salah baca meter"
                ),
                "billing.supervisor"
        );

        assertThat(journal.getStatus()).isEqualTo(JournalStatus.POSTED);
        assertThat(journal.getSourceModule()).isEqualTo("BILLING_VOID");
        assertThat(journal.getSourceRecordId()).isEqualTo(invoiceId);
        assertThat(journal.getSourceDocumentNumber()).isEqualTo("INV-202607-SR-0001");
        assertThat(journal.getLines()).hasSize(2);
        assertThat(journal.getLines().get(0).getAccountId()).isEqualTo(receivableAccountId);
        assertThat(journal.getLines().get(0).getCredit()).isEqualByComparingTo("46250.00");
        assertThat(journal.getLines().get(1).getAccountId()).isEqualTo(revenueAccountId);
        assertThat(journal.getLines().get(1).getDebit()).isEqualByComparingTo("46250.00");
        verify(ledgerEntryMaterializationService).materializePostedJournal(journal);
        verify(auditTrailService).record(
                "billing.supervisor",
                "ACCOUNTING",
                "POST_JOURNAL",
                journal.getId().toString(),
                "Salah baca meter"
        );
    }

    @Test
    void rejectsBillingInvoiceVoidWhenOriginalJournalDoesNotBelongToInvoice() {
        AccountingPeriod period = new AccountingPeriod("2026-07");
        UUID invoiceId = UUID.randomUUID();
        JournalEntry originalJournal = JournalEntry.draftFromSource(
                "BIL-INV-202607-SR-OTHER",
                period.getId(),
                "Issue invoice",
                "BILLING",
                UUID.randomUUID(),
                "INV-202607-SR-OTHER"
        );
        originalJournal.addLine(UUID.randomUUID(), new BigDecimal("1000.00"), BigDecimal.ZERO, "Piutang");
        originalJournal.addLine(UUID.randomUUID(), BigDecimal.ZERO, new BigDecimal("1000.00"), "Pendapatan");
        originalJournal.post(period, "billing.admin");

        when(journalEntryRepository.existsBySourceModuleAndSourceRecordId("BILLING_VOID", invoiceId)).thenReturn(false);
        when(accountingPeriodRepository.findByPeriod("2026-07")).thenReturn(Optional.of(period));
        when(journalEntryRepository.findWithLinesById(originalJournal.getId())).thenReturn(Optional.of(originalJournal));

        assertThatThrownBy(() -> service.postBillingInvoiceVoid(
                new BillingInvoiceVoidPostingCommand(
                        "INV-202607-SR-0001",
                        invoiceId,
                        "2026-07",
                        originalJournal.getId(),
                        "Salah baca meter"
                ),
                "billing.supervisor"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not belong to this invoice");

        verify(journalEntryRepository, never()).save(any(JournalEntry.class));
    }

    @Test
    void rejectsBillingInvoicePostingWhenSourceInvoiceAlreadyHasJournal() {
        UUID invoiceId = UUID.randomUUID();
        UUID receivableAccountId = UUID.randomUUID();
        UUID revenueAccountId = UUID.randomUUID();

        when(journalEntryRepository.existsBySourceModuleAndSourceRecordId("BILLING", invoiceId)).thenReturn(true);

        assertThatThrownBy(() -> service.postBillingInvoice(
                new BillingInvoicePostingCommand(
                        "INV-202607-SR-0001",
                        invoiceId,
                        "2026-07",
                        new BigDecimal("46250.00"),
                        receivableAccountId,
                        revenueAccountId,
                        "retry issue"
                ),
                "billing.admin"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already has a billing journal");

        verify(journalEntryRepository, never()).save(any(JournalEntry.class));
    }

    @Test
    void postsPaymentSettlementJournalWithSourceTraceabilityAndLedgerMaterialization() {
        AccountingPeriod period = new AccountingPeriod("2026-07");
        Account cashAccount = new Account("1-110", "Kas Loket", AccountType.ASSET);
        Account receivableAccount = new Account("1-130", "Piutang Air", AccountType.ASSET);
        UUID paymentId = UUID.randomUUID();

        when(journalEntryRepository.existsBySourceModuleAndSourceRecordId("PAYMENT", paymentId)).thenReturn(false);
        when(accountingPeriodRepository.findByPeriod("2026-07")).thenReturn(Optional.of(period));
        when(accountRepository.findById(cashAccount.getId())).thenReturn(Optional.of(cashAccount));
        when(accountRepository.findById(receivableAccount.getId())).thenReturn(Optional.of(receivableAccount));
        when(journalEntryRepository.findByJournalNumber("PMT-PAY-20260731-0001")).thenReturn(Optional.empty());
        when(journalEntryRepository.save(any(JournalEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JournalEntry journal = service.postPaymentSettlement(
                new PaymentSettlementPostingCommand(
                        "PAY-20260731-0001",
                        paymentId,
                        "2026-07",
                        new BigDecimal("100000.00"),
                        cashAccount.getId(),
                        receivableAccount.getId(),
                        "bayar loket"
                ),
                "kasir.loket"
        );

        assertThat(journal.getStatus()).isEqualTo(JournalStatus.POSTED);
        assertThat(journal.getSourceModule()).isEqualTo("PAYMENT");
        assertThat(journal.getSourceRecordId()).isEqualTo(paymentId);
        assertThat(journal.getSourceDocumentNumber()).isEqualTo("PAY-20260731-0001");
        assertThat(journal.getLines()).hasSize(2);
        assertThat(journal.getLines().get(0).getAccountId()).isEqualTo(cashAccount.getId());
        assertThat(journal.getLines().get(0).getDebit()).isEqualByComparingTo("100000.00");
        assertThat(journal.getLines().get(1).getAccountId()).isEqualTo(receivableAccount.getId());
        assertThat(journal.getLines().get(1).getCredit()).isEqualByComparingTo("100000.00");
        verify(ledgerEntryMaterializationService).materializePostedJournal(journal);
        verify(auditTrailService).record(
                "kasir.loket",
                "ACCOUNTING",
                "POST_JOURNAL",
                journal.getId().toString(),
                "bayar loket"
        );
    }

    @Test
    void postsPaymentReversalJournalWithOppositeReceivableAndCashLines() {
        AccountingPeriod period = new AccountingPeriod("2026-07");
        Account cashAccount = new Account("1-110", "Kas Loket", AccountType.ASSET);
        Account receivableAccount = new Account("1-130", "Piutang Air", AccountType.ASSET);
        UUID paymentId = UUID.randomUUID();

        when(journalEntryRepository.existsBySourceModuleAndSourceRecordId("PAYMENT_REVERSAL", paymentId)).thenReturn(false);
        when(accountingPeriodRepository.findByPeriod("2026-07")).thenReturn(Optional.of(period));
        when(accountRepository.findById(cashAccount.getId())).thenReturn(Optional.of(cashAccount));
        when(accountRepository.findById(receivableAccount.getId())).thenReturn(Optional.of(receivableAccount));
        when(journalEntryRepository.findByJournalNumber("REV-PAY-20260731-0001")).thenReturn(Optional.empty());
        when(journalEntryRepository.save(any(JournalEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JournalEntry journal = service.postPaymentReversal(
                new PaymentReversalPostingCommand(
                        "PAY-20260731-0001",
                        paymentId,
                        "2026-07",
                        new BigDecimal("100000.00"),
                        cashAccount.getId(),
                        receivableAccount.getId(),
                        "salah input pembayaran"
                ),
                "finance.supervisor"
        );

        assertThat(journal.getStatus()).isEqualTo(JournalStatus.POSTED);
        assertThat(journal.getSourceModule()).isEqualTo("PAYMENT_REVERSAL");
        assertThat(journal.getSourceRecordId()).isEqualTo(paymentId);
        assertThat(journal.getSourceDocumentNumber()).isEqualTo("PAY-20260731-0001");
        assertThat(journal.getLines()).hasSize(2);
        assertThat(journal.getLines().get(0).getAccountId()).isEqualTo(receivableAccount.getId());
        assertThat(journal.getLines().get(0).getDebit()).isEqualByComparingTo("100000.00");
        assertThat(journal.getLines().get(1).getAccountId()).isEqualTo(cashAccount.getId());
        assertThat(journal.getLines().get(1).getCredit()).isEqualByComparingTo("100000.00");
        verify(ledgerEntryMaterializationService).materializePostedJournal(journal);
        verify(auditTrailService).record(
                "finance.supervisor",
                "ACCOUNTING",
                "POST_JOURNAL",
                journal.getId().toString(),
            "salah input pembayaran"
        );
    }

    @Test
    void postsPaymentReconciliationAdjustmentJournalWithExplicitAccountsAndSourceTraceability() {
        AccountingPeriod period = new AccountingPeriod("2026-07");
        Account debitAccount = new Account("6-210", "Biaya Administrasi Bank", AccountType.EXPENSE);
        Account creditAccount = new Account("1-110", "Bank Operasional", AccountType.ASSET);
        UUID itemId = UUID.randomUUID();

        when(journalEntryRepository.existsBySourceModuleAndSourceRecordId("PAYMENT_RECONCILIATION_ADJUSTMENT", itemId)).thenReturn(false);
        when(accountingPeriodRepository.findByPeriod("2026-07")).thenReturn(Optional.of(period));
        when(accountRepository.findById(debitAccount.getId())).thenReturn(Optional.of(debitAccount));
        when(accountRepository.findById(creditAccount.getId())).thenReturn(Optional.of(creditAccount));
        when(journalEntryRepository.findByJournalNumber("REC-ADJ-REC-20260731-0001-ROW-7")).thenReturn(Optional.empty());
        when(journalEntryRepository.save(any(JournalEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JournalEntry journal = service.postPaymentReconciliationAdjustment(
                new PaymentReconciliationAdjustmentPostingCommand(
                        itemId,
                        "REC-20260731-0001",
                        7,
                        "2026-07",
                        new BigDecimal("2500.00"),
                        debitAccount.getId(),
                        creditAccount.getId(),
                        "Biaya admin bank atas mutasi rekonsiliasi row 7."
                ),
                "finance.supervisor"
        );

        assertThat(journal.getStatus()).isEqualTo(JournalStatus.POSTED);
        assertThat(journal.getSourceModule()).isEqualTo("PAYMENT_RECONCILIATION_ADJUSTMENT");
        assertThat(journal.getSourceRecordId()).isEqualTo(itemId);
        assertThat(journal.getSourceDocumentNumber()).isEqualTo("REC-20260731-0001-ROW-7");
        assertThat(journal.getLines()).hasSize(2);
        assertThat(journal.getLines().get(0).getAccountId()).isEqualTo(debitAccount.getId());
        assertThat(journal.getLines().get(0).getDebit()).isEqualByComparingTo("2500.00");
        assertThat(journal.getLines().get(1).getAccountId()).isEqualTo(creditAccount.getId());
        assertThat(journal.getLines().get(1).getCredit()).isEqualByComparingTo("2500.00");
        verify(ledgerEntryMaterializationService).materializePostedJournal(journal);
        verify(auditTrailService).record(
                "finance.supervisor",
                "ACCOUNTING",
                "POST_JOURNAL",
                journal.getId().toString(),
                "Biaya admin bank atas mutasi rekonsiliasi row 7."
        );
    }

    @Test
    void rejectsPaymentReconciliationAdjustmentWhenSourceItemAlreadyHasJournal() {
        UUID itemId = UUID.randomUUID();

        when(journalEntryRepository.existsBySourceModuleAndSourceRecordId("PAYMENT_RECONCILIATION_ADJUSTMENT", itemId)).thenReturn(true);

        assertThatThrownBy(() -> service.postPaymentReconciliationAdjustment(
                new PaymentReconciliationAdjustmentPostingCommand(
                        itemId,
                        "REC-20260731-0001",
                        7,
                        "2026-07",
                        new BigDecimal("2500.00"),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "retry adjustment"
                ),
                "finance.supervisor"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already has an adjustment journal");

        verify(journalEntryRepository, never()).save(any(JournalEntry.class));
    }

    @Test
    void rejectsPaymentReconciliationAdjustmentWhenAccountingPeriodIsLocked() {
        AccountingPeriod period = new AccountingPeriod("2026-07");
        period.startClosingReview();
        period.lock();
        Account debitAccount = new Account("6-210", "Biaya Administrasi Bank", AccountType.EXPENSE);
        Account creditAccount = new Account("1-110", "Bank Operasional", AccountType.ASSET);
        UUID itemId = UUID.randomUUID();

        when(journalEntryRepository.existsBySourceModuleAndSourceRecordId("PAYMENT_RECONCILIATION_ADJUSTMENT", itemId)).thenReturn(false);
        when(accountingPeriodRepository.findByPeriod("2026-07")).thenReturn(Optional.of(period));
        when(accountRepository.findById(debitAccount.getId())).thenReturn(Optional.of(debitAccount));
        when(accountRepository.findById(creditAccount.getId())).thenReturn(Optional.of(creditAccount));
        when(journalEntryRepository.findByJournalNumber("REC-ADJ-REC-20260731-0001-ROW-7")).thenReturn(Optional.empty());
        when(journalEntryRepository.save(any(JournalEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.postPaymentReconciliationAdjustment(
                new PaymentReconciliationAdjustmentPostingCommand(
                        itemId,
                        "REC-20260731-0001",
                        7,
                        "2026-07",
                        new BigDecimal("2500.00"),
                        debitAccount.getId(),
                        creditAccount.getId(),
                        "locked period adjustment"
                ),
                "finance.supervisor"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Posting is not allowed in this period");

        verify(ledgerEntryMaterializationService, never()).materializePostedJournal(any(JournalEntry.class));
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
