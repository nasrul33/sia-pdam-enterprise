package id.pdam.sia.accounting.application;

import id.pdam.sia.accounting.domain.Account;
import id.pdam.sia.accounting.domain.AccountType;
import id.pdam.sia.accounting.domain.AccountingPeriod;
import id.pdam.sia.accounting.domain.FixedAsset;
import id.pdam.sia.accounting.domain.FixedAssetDepreciation;
import id.pdam.sia.accounting.domain.FixedAssetDepreciationMethod;
import id.pdam.sia.accounting.domain.FixedAssetStatus;
import id.pdam.sia.accounting.domain.JournalEntry;
import id.pdam.sia.accounting.domain.JournalStatus;
import id.pdam.sia.accounting.domain.Payable;
import id.pdam.sia.accounting.domain.PayableStatus;
import id.pdam.sia.accounting.domain.Supplier;
import id.pdam.sia.accounting.repository.AccountRepository;
import id.pdam.sia.accounting.repository.AccountingPeriodRepository;
import id.pdam.sia.accounting.repository.FixedAssetDepreciationRepository;
import id.pdam.sia.accounting.repository.FixedAssetRepository;
import id.pdam.sia.accounting.repository.JournalEntryRepository;
import id.pdam.sia.accounting.repository.PayableRepository;
import id.pdam.sia.accounting.repository.SupplierRepository;
import id.pdam.sia.reporting.application.LedgerEntryMaterializationService;
import id.pdam.sia.reporting.domain.LedgerEntry;
import id.pdam.sia.reporting.repository.LedgerEntryRepository;
import id.pdam.sia.shared.audit.AuditTrailService;
import id.pdam.sia.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
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

class AccountingBlueprintApplicationServiceTest {
    private final SupplierRepository supplierRepository = mock(SupplierRepository.class);
    private final PayableRepository payableRepository = mock(PayableRepository.class);
    private final FixedAssetRepository fixedAssetRepository = mock(FixedAssetRepository.class);
    private final FixedAssetDepreciationRepository fixedAssetDepreciationRepository =
            mock(FixedAssetDepreciationRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final AccountingPeriodRepository accountingPeriodRepository = mock(AccountingPeriodRepository.class);
    private final JournalEntryRepository journalEntryRepository = mock(JournalEntryRepository.class);
    private final LedgerEntryRepository ledgerEntryRepository = mock(LedgerEntryRepository.class);
    private final AuditTrailService auditTrailService = mock(AuditTrailService.class);
    private final LedgerEntryMaterializationService ledgerEntryMaterializationService =
            mock(LedgerEntryMaterializationService.class);
    private final PostingService postingService = new PostingService(auditTrailService, ledgerEntryMaterializationService);

    private final AccountingBlueprintApplicationService service = new AccountingBlueprintApplicationService(
            supplierRepository,
            payableRepository,
            fixedAssetRepository,
            fixedAssetDepreciationRepository,
            accountRepository,
            accountingPeriodRepository,
            journalEntryRepository,
            ledgerEntryRepository,
            postingService,
            auditTrailService
    );

    @Test
    void recordPayablePostsLiabilityJournalAndPersistsOpenPayable() {
        AccountingPeriod period = new AccountingPeriod("2026-07");
        Supplier supplier = new Supplier("SUP-001", "Vendor Pipa", "Rina", "0800");
        Account expenseAccount = new Account("6-110", "Beban Pemeliharaan", AccountType.EXPENSE);
        Account payableAccount = new Account("2-110", "Utang Usaha", AccountType.LIABILITY);

        when(supplierRepository.findById(supplier.getId())).thenReturn(Optional.of(supplier));
        when(payableRepository.findByPayableNumber("PBL-2026-0001")).thenReturn(Optional.empty());
        when(accountingPeriodRepository.findByPeriod("2026-07")).thenReturn(Optional.of(period));
        when(accountRepository.findById(expenseAccount.getId())).thenReturn(Optional.of(expenseAccount));
        when(accountRepository.findById(payableAccount.getId())).thenReturn(Optional.of(payableAccount));
        when(journalEntryRepository.findByJournalNumber("AP-PBL-2026-0001")).thenReturn(Optional.empty());
        when(journalEntryRepository.save(any(JournalEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(payableRepository.save(any(Payable.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payable payable = service.recordPayable(
                new AccountingBlueprintApplicationService.RecordPayableCommand(
                        supplier.getId(),
                        "PBL-2026-0001",
                        "INV-VENDOR-1",
                        "2026-07",
                        new BigDecimal("1250000.00"),
                        "Pembelian material pipa",
                        expenseAccount.getId(),
                        payableAccount.getId(),
                        "record vendor invoice"
                ),
                "finance.ap"
        );

        ArgumentCaptor<JournalEntry> journalCaptor = ArgumentCaptor.forClass(JournalEntry.class);
        verify(journalEntryRepository).save(journalCaptor.capture());
        JournalEntry journal = journalCaptor.getValue();

        assertThat(journal.getStatus()).isEqualTo(JournalStatus.POSTED);
        assertThat(journal.getSourceModule()).isEqualTo("PAYABLE");
        assertThat(journal.getSourceDocumentNumber()).isEqualTo("PBL-2026-0001");
        assertThat(journal.getLines()).hasSize(2);
        assertThat(journal.getLines().get(0).getAccountId()).isEqualTo(expenseAccount.getId());
        assertThat(journal.getLines().get(0).getDebit()).isEqualByComparingTo("1250000.00");
        assertThat(journal.getLines().get(1).getAccountId()).isEqualTo(payableAccount.getId());
        assertThat(journal.getLines().get(1).getCredit()).isEqualByComparingTo("1250000.00");
        assertThat(payable.getStatus()).isEqualTo(PayableStatus.OPEN);
        assertThat(payable.getRecordedJournalEntryId()).isEqualTo(journal.getId());
        verify(ledgerEntryMaterializationService).materializePostedJournal(journal);
        verify(auditTrailService).record("finance.ap", "ACCOUNTING", "RECORD_PAYABLE", payable.getId().toString(), "record vendor invoice");
    }

    @Test
    void settlePayablePostsCashOutAndClosesPayable() {
        AccountingPeriod period = new AccountingPeriod("2026-07");
        Account payableAccount = new Account("2-110", "Utang Usaha", AccountType.LIABILITY);
        Account cashAccount = new Account("1-110", "Bank Operasional", AccountType.ASSET);
        Payable payable = new Payable(
                UUID.randomUUID(),
                "PBL-2026-0002",
                "INV-VENDOR-2",
                "2026-07",
                new BigDecimal("500000.00"),
                "Jasa perbaikan",
                UUID.randomUUID(),
                "finance.ap"
        );

        when(payableRepository.findById(payable.getId())).thenReturn(Optional.of(payable));
        when(accountRepository.findById(payableAccount.getId())).thenReturn(Optional.of(payableAccount));
        when(accountRepository.findById(cashAccount.getId())).thenReturn(Optional.of(cashAccount));
        when(accountingPeriodRepository.findByPeriod("2026-07")).thenReturn(Optional.of(period));
        when(journalEntryRepository.findByJournalNumber("AP-SETTLE-PBL-2026-0002")).thenReturn(Optional.empty());
        when(journalEntryRepository.save(any(JournalEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(payableRepository.save(any(Payable.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payable settled = service.settlePayable(
                payable.getId(),
                new AccountingBlueprintApplicationService.SettlePayableCommand(
                        payableAccount.getId(),
                        cashAccount.getId(),
                        "settle payable"
                ),
                "finance.cash"
        );

        ArgumentCaptor<JournalEntry> journalCaptor = ArgumentCaptor.forClass(JournalEntry.class);
        verify(journalEntryRepository).save(journalCaptor.capture());
        JournalEntry journal = journalCaptor.getValue();

        assertThat(journal.getStatus()).isEqualTo(JournalStatus.POSTED);
        assertThat(journal.getSourceModule()).isEqualTo("PAYABLE_SETTLEMENT");
        assertThat(journal.getSourceRecordId()).isEqualTo(payable.getId());
        assertThat(journal.getLines().get(0).getAccountId()).isEqualTo(payableAccount.getId());
        assertThat(journal.getLines().get(0).getDebit()).isEqualByComparingTo("500000.00");
        assertThat(journal.getLines().get(1).getAccountId()).isEqualTo(cashAccount.getId());
        assertThat(journal.getLines().get(1).getCredit()).isEqualByComparingTo("500000.00");
        assertThat(settled.getStatus()).isEqualTo(PayableStatus.PAID);
        assertThat(settled.getSettlementJournalEntryId()).isEqualTo(journal.getId());
        verify(auditTrailService).record("finance.cash", "ACCOUNTING", "SETTLE_PAYABLE", settled.getId().toString(), "settle payable");
    }

    @Test
    void postFixedAssetDepreciationUsesCalculatedAmountAndBlocksDuplicatePeriod() {
        AccountingPeriod period = new AccountingPeriod("2026-07");
        Account accumulatedAccount = new Account("1-199", "Akumulasi Penyusutan", AccountType.ASSET);
        Account expenseAccount = new Account("6-300", "Beban Penyusutan", AccountType.EXPENSE);
        FixedAsset asset = new FixedAsset(
                "FA-PUMP-001",
                "Pompa Distribusi",
                LocalDate.of(2026, 1, 1),
                new BigDecimal("12000000.00"),
                BigDecimal.ZERO,
                12,
                FixedAssetDepreciationMethod.STRAIGHT_LINE,
                UUID.randomUUID(),
                accumulatedAccount.getId(),
                expenseAccount.getId(),
                UUID.randomUUID()
        );

        when(fixedAssetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        when(fixedAssetDepreciationRepository.existsByAssetIdAndPeriod(asset.getId(), "2026-07")).thenReturn(false);
        when(accountingPeriodRepository.findByPeriod("2026-07")).thenReturn(Optional.of(period));
        when(accountRepository.findById(expenseAccount.getId())).thenReturn(Optional.of(expenseAccount));
        when(accountRepository.findById(accumulatedAccount.getId())).thenReturn(Optional.of(accumulatedAccount));
        when(journalEntryRepository.findByJournalNumber("FA-DEP-FA-PUMP-001-2026-07")).thenReturn(Optional.empty());
        when(journalEntryRepository.save(any(JournalEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fixedAssetRepository.save(any(FixedAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fixedAssetDepreciationRepository.save(any(FixedAssetDepreciation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FixedAssetDepreciation depreciation = service.postFixedAssetDepreciation(
                asset.getId(),
                new AccountingBlueprintApplicationService.PostAssetDepreciationCommand(
                        "2026-07",
                        null,
                        "monthly depreciation"
                ),
                "asset.accountant"
        );

        ArgumentCaptor<JournalEntry> journalCaptor = ArgumentCaptor.forClass(JournalEntry.class);
        verify(journalEntryRepository).save(journalCaptor.capture());
        JournalEntry journal = journalCaptor.getValue();

        assertThat(depreciation.getAmount()).isEqualByComparingTo("1000000.00");
        assertThat(asset.getAccumulatedDepreciation()).isEqualByComparingTo("1000000.00");
        assertThat(journal.getSourceModule()).isEqualTo("FIXED_ASSET_DEPRECIATION");
        assertThat(journal.getSourceDocumentNumber()).isEqualTo("FA-PUMP-001-2026-07");
        assertThat(journal.getLines().get(0).getDebit()).isEqualByComparingTo("1000000.00");
        assertThat(journal.getLines().get(1).getCredit()).isEqualByComparingTo("1000000.00");

        when(fixedAssetDepreciationRepository.existsByAssetIdAndPeriod(asset.getId(), "2026-08")).thenReturn(true);

        assertThatThrownBy(() -> service.postFixedAssetDepreciation(
                asset.getId(),
                new AccountingBlueprintApplicationService.PostAssetDepreciationCommand("2026-08", null, "duplicate"),
                "asset.accountant"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void reverseJournalCreatesOppositePostedLinesOnce() {
        AccountingPeriod period = new AccountingPeriod("2026-07");
        UUID debitAccountId = UUID.randomUUID();
        UUID creditAccountId = UUID.randomUUID();
        JournalEntry original = JournalEntry.draftFromSource(
                "JV-ORIGINAL",
                period.getId(),
                "Original posted journal",
                "ACCOUNTING",
                UUID.randomUUID(),
                "JV-ORIGINAL"
        );
        original.addLine(debitAccountId, new BigDecimal("900000.00"), BigDecimal.ZERO, "debit");
        original.addLine(creditAccountId, BigDecimal.ZERO, new BigDecimal("900000.00"), "credit");
        original.post(period, "finance.supervisor");

        when(journalEntryRepository.findWithLinesById(original.getId())).thenReturn(Optional.of(original));
        when(journalEntryRepository.existsBySourceModuleAndSourceRecordId("JOURNAL_REVERSAL", original.getId())).thenReturn(false);
        when(accountingPeriodRepository.findByPeriod("2026-07")).thenReturn(Optional.of(period));
        when(accountRepository.findById(debitAccountId)).thenReturn(Optional.of(new Account("1-101", "Kas", AccountType.ASSET)));
        when(accountRepository.findById(creditAccountId)).thenReturn(Optional.of(new Account("4-101", "Pendapatan", AccountType.REVENUE)));
        when(journalEntryRepository.findByJournalNumber("JRN-REV-JV-ORIGINAL")).thenReturn(Optional.empty());
        when(journalEntryRepository.save(any(JournalEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JournalEntry reversal = service.reverseJournal(
                original.getId(),
                new AccountingBlueprintApplicationService.ReverseJournalCommand("2026-07", "reverse incorrect journal"),
                "finance.supervisor"
        );

        assertThat(reversal.getStatus()).isEqualTo(JournalStatus.POSTED);
        assertThat(reversal.getSourceModule()).isEqualTo("JOURNAL_REVERSAL");
        assertThat(reversal.getSourceRecordId()).isEqualTo(original.getId());
        assertThat(reversal.getLines().get(0).getAccountId()).isEqualTo(debitAccountId);
        assertThat(reversal.getLines().get(0).getCredit()).isEqualByComparingTo("900000.00");
        assertThat(reversal.getLines().get(1).getAccountId()).isEqualTo(creditAccountId);
        assertThat(reversal.getLines().get(1).getDebit()).isEqualByComparingTo("900000.00");
    }

    @Test
    void postClosingEntriesClosesRevenueAndExpenseIntoEquity() {
        AccountingPeriod period = new AccountingPeriod("2026-07");
        Account revenueAccount = new Account("4-100", "Pendapatan Air", AccountType.REVENUE);
        Account expenseAccount = new Account("6-100", "Beban Operasi", AccountType.EXPENSE);
        Account retainedEarnings = new Account("3-100", "Saldo Laba", AccountType.EQUITY);
        LedgerEntry revenueLedger = new LedgerEntry(
                UUID.randomUUID(),
                UUID.randomUUID(),
                revenueAccount.getId(),
                LocalDate.of(2026, 7, 15),
                BigDecimal.ZERO,
                new BigDecimal("2000000.00"),
                "BILLING",
                UUID.randomUUID()
        );
        LedgerEntry expenseLedger = new LedgerEntry(
                UUID.randomUUID(),
                UUID.randomUUID(),
                expenseAccount.getId(),
                LocalDate.of(2026, 7, 20),
                new BigDecimal("750000.00"),
                BigDecimal.ZERO,
                "ACCOUNTING",
                UUID.randomUUID()
        );

        when(accountRepository.findById(retainedEarnings.getId())).thenReturn(Optional.of(retainedEarnings));
        when(ledgerEntryRepository.findByPostingDateBetween(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)))
                .thenReturn(List.of(revenueLedger, expenseLedger));
        when(accountRepository.findAllById(any())).thenReturn(List.of(revenueAccount, expenseAccount));
        when(accountingPeriodRepository.findByPeriod("2026-07")).thenReturn(Optional.of(period));
        when(accountRepository.findById(revenueAccount.getId())).thenReturn(Optional.of(revenueAccount));
        when(accountRepository.findById(expenseAccount.getId())).thenReturn(Optional.of(expenseAccount));
        when(journalEntryRepository.findByJournalNumber("CL-2026-07")).thenReturn(Optional.empty());
        when(journalEntryRepository.save(any(JournalEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JournalEntry closing = service.postClosingEntries(
                new AccountingBlueprintApplicationService.PostClosingEntryCommand(
                        "2026-07",
                        retainedEarnings.getId(),
                        "month end close"
                ),
                "finance.manager"
        );

        assertThat(closing.getStatus()).isEqualTo(JournalStatus.POSTED);
        assertThat(closing.getSourceModule()).isEqualTo("CLOSING_ENTRY");
        assertThat(closing.totalDebit()).isEqualByComparingTo("2750000.00");
        assertThat(closing.totalCredit()).isEqualByComparingTo("2750000.00");
        assertThat(closing.getLines()).anySatisfy(line -> {
            assertThat(line.getAccountId()).isEqualTo(revenueAccount.getId());
            assertThat(line.getDebit()).isEqualByComparingTo("2000000.00");
        });
        assertThat(closing.getLines()).anySatisfy(line -> {
            assertThat(line.getAccountId()).isEqualTo(expenseAccount.getId());
            assertThat(line.getCredit()).isEqualByComparingTo("750000.00");
        });
    }

    @Test
    void postOpeningBalanceRejectsDuplicateSourcePeriod() {
        when(journalEntryRepository.existsBySourceModuleAndSourceRecordId(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.postOpeningBalance(
                new AccountingBlueprintApplicationService.PostOpeningBalanceCommand(
                        "2026-07",
                        List.of(new AccountingBlueprintApplicationService.PostOpeningBalanceLineCommand(
                                UUID.randomUUID(),
                                new BigDecimal("1000.00"),
                                BigDecimal.ZERO,
                                "opening cash"
                        )),
                        "opening balance"
                ),
                "finance.manager"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");

        verify(journalEntryRepository, never()).save(any(JournalEntry.class));
    }
}
