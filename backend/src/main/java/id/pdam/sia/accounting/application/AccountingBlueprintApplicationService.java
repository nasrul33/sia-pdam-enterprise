package id.pdam.sia.accounting.application;

import id.pdam.sia.accounting.domain.Account;
import id.pdam.sia.accounting.domain.AccountType;
import id.pdam.sia.accounting.domain.AccountingPeriod;
import id.pdam.sia.accounting.domain.FixedAsset;
import id.pdam.sia.accounting.domain.FixedAssetDepreciation;
import id.pdam.sia.accounting.domain.FixedAssetDepreciationMethod;
import id.pdam.sia.accounting.domain.FixedAssetStatus;
import id.pdam.sia.accounting.domain.JournalEntry;
import id.pdam.sia.accounting.domain.JournalLine;
import id.pdam.sia.accounting.domain.JournalStatus;
import id.pdam.sia.accounting.domain.Payable;
import id.pdam.sia.accounting.domain.PayableStatus;
import id.pdam.sia.accounting.domain.Supplier;
import id.pdam.sia.accounting.domain.SupplierStatus;
import id.pdam.sia.accounting.repository.AccountRepository;
import id.pdam.sia.accounting.repository.AccountingPeriodRepository;
import id.pdam.sia.accounting.repository.FixedAssetDepreciationRepository;
import id.pdam.sia.accounting.repository.FixedAssetRepository;
import id.pdam.sia.accounting.repository.JournalEntryRepository;
import id.pdam.sia.accounting.repository.PayableRepository;
import id.pdam.sia.accounting.repository.SupplierRepository;
import id.pdam.sia.reporting.domain.LedgerEntry;
import id.pdam.sia.reporting.repository.LedgerEntryRepository;
import id.pdam.sia.shared.audit.AuditTrailService;
import id.pdam.sia.shared.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AccountingBlueprintApplicationService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM");

    private final SupplierRepository supplierRepository;
    private final PayableRepository payableRepository;
    private final FixedAssetRepository fixedAssetRepository;
    private final FixedAssetDepreciationRepository fixedAssetDepreciationRepository;
    private final AccountRepository accountRepository;
    private final AccountingPeriodRepository accountingPeriodRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final PostingService postingService;
    private final AuditTrailService auditTrailService;

    public AccountingBlueprintApplicationService(
            SupplierRepository supplierRepository,
            PayableRepository payableRepository,
            FixedAssetRepository fixedAssetRepository,
            FixedAssetDepreciationRepository fixedAssetDepreciationRepository,
            AccountRepository accountRepository,
            AccountingPeriodRepository accountingPeriodRepository,
            JournalEntryRepository journalEntryRepository,
            LedgerEntryRepository ledgerEntryRepository,
            PostingService postingService,
            AuditTrailService auditTrailService
    ) {
        this.supplierRepository = supplierRepository;
        this.payableRepository = payableRepository;
        this.fixedAssetRepository = fixedAssetRepository;
        this.fixedAssetDepreciationRepository = fixedAssetDepreciationRepository;
        this.accountRepository = accountRepository;
        this.accountingPeriodRepository = accountingPeriodRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.postingService = postingService;
        this.auditTrailService = auditTrailService;
    }

    @Transactional(readOnly = true)
    public Page<Supplier> listSuppliers(SupplierStatus status, int page, int size) {
        Pageable pageable = pageable(page, size, Sort.by("code").ascending());
        if (status == null) {
            return supplierRepository.findAll(pageable);
        }
        return supplierRepository.findByStatus(status, pageable);
    }

    @Transactional
    public Supplier createSupplier(CreateSupplierCommand command, String actor) {
        String code = requireNormalize(command.code(), "SUPPLIER_CODE_REQUIRED", "Supplier code is required.");
        supplierRepository.findByCode(code).ifPresent(existing -> {
            throw new BusinessException("SUPPLIER_CODE_DUPLICATE", "Supplier code already exists.");
        });
        Supplier supplier = supplierRepository.save(new Supplier(code, command.name(), command.contactName(), command.phoneNumber()));
        auditTrailService.record(actor, "ACCOUNTING", "CREATE_SUPPLIER", supplier.getId().toString(), command.reason());
        return supplier;
    }

    @Transactional(readOnly = true)
    public Page<Payable> listPayables(String period, PayableStatus status, int page, int size) {
        Pageable pageable = pageable(page, size, Sort.by("recordedAt").descending());
        String normalizedPeriod = normalizeOptionalPeriod(period);
        if (normalizedPeriod != null && status != null) {
            return payableRepository.findByPeriodAndStatus(normalizedPeriod, status, pageable);
        }
        if (normalizedPeriod != null) {
            return payableRepository.findByPeriod(normalizedPeriod, pageable);
        }
        if (status != null) {
            return payableRepository.findByStatus(status, pageable);
        }
        return payableRepository.findAll(pageable);
    }

    @Transactional
    public Payable recordPayable(RecordPayableCommand command, String actor) {
        Supplier supplier = supplierRepository.findById(requireUuid(command.supplierId(), "PAYABLE_SUPPLIER_REQUIRED", "Payable supplier is required."))
                .orElseThrow(() -> new BusinessException("PAYABLE_SUPPLIER_NOT_FOUND", "Payable supplier was not found."));
        if (supplier.getStatus() != SupplierStatus.ACTIVE) {
            throw new BusinessException("PAYABLE_SUPPLIER_INACTIVE", "Payable supplier must be active.");
        }
        String payableNumber = requireNormalize(command.payableNumber(), "PAYABLE_NUMBER_REQUIRED", "Payable number is required.");
        payableRepository.findByPayableNumber(payableNumber).ifPresent(existing -> {
            throw new BusinessException("PAYABLE_NUMBER_DUPLICATE", "Payable number already exists.");
        });
        String period = normalizePeriod(command.period(), "PAYABLE_PERIOD_REQUIRED", "Payable period is required.");
        BigDecimal amount = requirePositive(command.amount(), "PAYABLE_AMOUNT_REQUIRED", "Payable amount is required.");
        Account debitAccount = findAccount(command.debitAccountId());
        Account payableAccount = findAccount(command.payableAccountId());
        requireOneOf(debitAccount, "PAYABLE_DEBIT_ACCOUNT_INVALID", "Payable debit account must be asset or expense.", AccountType.ASSET, AccountType.EXPENSE);
        requireType(payableAccount, AccountType.LIABILITY, "PAYABLE_ACCOUNT_INVALID", "Payable account must be liability.");

        UUID sourceRecordId = UUID.randomUUID();
        JournalEntry journal = postSourceJournal(
                "AP-" + payableNumber,
                period,
                "Record payable " + payableNumber,
                "PAYABLE",
                sourceRecordId,
                payableNumber,
                List.of(
                        JournalPostingLine.debit(debitAccount.getId(), amount, "Beban/aset " + payableNumber),
                        JournalPostingLine.credit(payableAccount.getId(), amount, "Utang usaha " + payableNumber)
                ),
                actor,
                command.reason()
        );
        Payable payable = payableRepository.save(new Payable(
                supplier.getId(),
                payableNumber,
                command.supplierReference(),
                period,
                amount,
                command.description(),
                journal.getId(),
                actor
        ));
        auditTrailService.record(actor, "ACCOUNTING", "RECORD_PAYABLE", payable.getId().toString(), command.reason());
        return payable;
    }

    @Transactional
    public Payable settlePayable(UUID payableId, SettlePayableCommand command, String actor) {
        Payable payable = payableRepository.findById(requireUuid(payableId, "PAYABLE_ID_REQUIRED", "Payable id is required."))
                .orElseThrow(() -> new BusinessException("PAYABLE_NOT_FOUND", "Payable was not found."));
        Account payableAccount = findAccount(command.payableAccountId());
        Account cashAccount = findAccount(command.cashAccountId());
        requireType(payableAccount, AccountType.LIABILITY, "PAYABLE_ACCOUNT_INVALID", "Payable account must be liability.");
        requireType(cashAccount, AccountType.ASSET, "PAYABLE_CASH_ACCOUNT_INVALID", "Payable settlement cash/bank account must be asset.");
        JournalEntry journal = postSourceJournal(
                sourceJournalNumber("AP-SETTLE-", payable.getPayableNumber()),
                payable.getPeriod(),
                "Settle payable " + payable.getPayableNumber(),
                "PAYABLE_SETTLEMENT",
                payable.getId(),
                payable.getPayableNumber(),
                List.of(
                        JournalPostingLine.debit(payableAccount.getId(), payable.getAmount(), "Pelunasan utang " + payable.getPayableNumber()),
                        JournalPostingLine.credit(cashAccount.getId(), payable.getAmount(), "Kas/bank keluar " + payable.getPayableNumber())
                ),
                actor,
                command.reason()
        );
        payable.settle(journal.getId(), actor);
        Payable saved = payableRepository.save(payable);
        auditTrailService.record(actor, "ACCOUNTING", "SETTLE_PAYABLE", saved.getId().toString(), command.reason());
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<FixedAsset> listFixedAssets(FixedAssetStatus status, int page, int size) {
        Pageable pageable = pageable(page, size, Sort.by("assetCode").ascending());
        if (status == null) {
            return fixedAssetRepository.findAll(pageable);
        }
        return fixedAssetRepository.findByStatus(status, pageable);
    }

    @Transactional
    public FixedAsset registerFixedAsset(RegisterFixedAssetCommand command, String actor) {
        String assetCode = requireNormalize(command.assetCode(), "FIXED_ASSET_CODE_REQUIRED", "Fixed asset code is required.");
        fixedAssetRepository.findByAssetCode(assetCode).ifPresent(existing -> {
            throw new BusinessException("FIXED_ASSET_CODE_DUPLICATE", "Fixed asset code already exists.");
        });
        String period = normalizePeriod(command.period(), "FIXED_ASSET_PERIOD_REQUIRED", "Fixed asset accounting period is required.");
        BigDecimal cost = requirePositive(command.acquisitionCost(), "FIXED_ASSET_COST_REQUIRED", "Fixed asset acquisition cost is required.");
        Account assetAccount = findAccount(command.assetAccountId());
        Account creditAccount = findAccount(command.creditAccountId());
        Account accumulatedAccount = findAccount(command.accumulatedDepreciationAccountId());
        Account expenseAccount = findAccount(command.depreciationExpenseAccountId());
        requireType(assetAccount, AccountType.ASSET, "FIXED_ASSET_ACCOUNT_INVALID", "Fixed asset account must be asset.");
        requireOneOf(creditAccount, "FIXED_ASSET_CREDIT_ACCOUNT_INVALID", "Fixed asset credit account must be asset or liability.", AccountType.ASSET, AccountType.LIABILITY);
        requireType(expenseAccount, AccountType.EXPENSE, "FIXED_ASSET_EXPENSE_ACCOUNT_INVALID", "Depreciation expense account must be expense.");
        requireOneOf(accumulatedAccount, "FIXED_ASSET_ACCUMULATED_ACCOUNT_INVALID", "Accumulated depreciation account must be an asset or liability account.", AccountType.ASSET, AccountType.LIABILITY);

        UUID sourceRecordId = UUID.randomUUID();
        JournalEntry journal = postSourceJournal(
                sourceJournalNumber("FA-REG-", assetCode),
                period,
                "Register fixed asset " + assetCode,
                "FIXED_ASSET_REGISTRATION",
                sourceRecordId,
                assetCode,
                List.of(
                        JournalPostingLine.debit(assetAccount.getId(), cost, "Perolehan aset " + assetCode),
                        JournalPostingLine.credit(creditAccount.getId(), cost, "Sumber perolehan aset " + assetCode)
                ),
                actor,
                command.reason()
        );
        FixedAsset asset = fixedAssetRepository.save(new FixedAsset(
                assetCode,
                command.name(),
                command.acquisitionDate(),
                cost,
                command.salvageValue(),
                command.usefulLifeMonths(),
                command.depreciationMethod(),
                assetAccount.getId(),
                accumulatedAccount.getId(),
                expenseAccount.getId(),
                journal.getId()
        ));
        auditTrailService.record(actor, "ACCOUNTING", "REGISTER_FIXED_ASSET", asset.getId().toString(), command.reason());
        return asset;
    }

    @Transactional
    public FixedAssetDepreciation postFixedAssetDepreciation(UUID assetId, PostAssetDepreciationCommand command, String actor) {
        FixedAsset asset = fixedAssetRepository.findById(requireUuid(assetId, "FIXED_ASSET_ID_REQUIRED", "Fixed asset id is required."))
                .orElseThrow(() -> new BusinessException("FIXED_ASSET_NOT_FOUND", "Fixed asset was not found."));
        String period = normalizePeriod(command.period(), "FIXED_ASSET_DEPRECIATION_PERIOD_REQUIRED", "Fixed asset depreciation period is required.");
        if (fixedAssetDepreciationRepository.existsByAssetIdAndPeriod(asset.getId(), period)) {
            throw new BusinessException("FIXED_ASSET_DEPRECIATION_DUPLICATE", "Fixed asset depreciation already exists for this period.");
        }
        BigDecimal amount = command.amount() == null ? asset.nextDepreciationAmount() : requirePositive(command.amount(), "FIXED_ASSET_DEPRECIATION_AMOUNT_REQUIRED", "Fixed asset depreciation amount is required.");
        JournalEntry journal = postSourceJournal(
                sourceJournalNumber("FA-DEP-", asset.getAssetCode() + "-" + period),
                period,
                "Post depreciation " + asset.getAssetCode() + " " + period,
                "FIXED_ASSET_DEPRECIATION",
                deterministicUuid("FIXED_ASSET_DEPRECIATION:" + asset.getId() + ":" + period),
                asset.getAssetCode() + "-" + period,
                List.of(
                        JournalPostingLine.debit(asset.getDepreciationExpenseAccountId(), amount, "Beban penyusutan " + asset.getAssetCode()),
                        JournalPostingLine.credit(asset.getAccumulatedDepreciationAccountId(), amount, "Akumulasi penyusutan " + asset.getAssetCode())
                ),
                actor,
                command.reason()
        );
        asset.postDepreciation(amount);
        fixedAssetRepository.save(asset);
        FixedAssetDepreciation depreciation = fixedAssetDepreciationRepository.save(new FixedAssetDepreciation(
                asset.getId(),
                period,
                amount,
                journal.getId(),
                actor
        ));
        auditTrailService.record(actor, "ACCOUNTING", "POST_FIXED_ASSET_DEPRECIATION", depreciation.getId().toString(), command.reason());
        return depreciation;
    }

    @Transactional
    public FixedAsset disposeFixedAsset(UUID assetId, DisposeFixedAssetCommand command, String actor) {
        FixedAsset asset = fixedAssetRepository.findById(requireUuid(assetId, "FIXED_ASSET_ID_REQUIRED", "Fixed asset id is required."))
                .orElseThrow(() -> new BusinessException("FIXED_ASSET_NOT_FOUND", "Fixed asset was not found."));
        String period = normalizePeriod(command.period(), "FIXED_ASSET_DISPOSAL_PERIOD_REQUIRED", "Fixed asset disposal period is required.");
        String reason = requireNormalize(command.reason(), "FIXED_ASSET_DISPOSAL_REASON_REQUIRED", "Fixed asset disposal reason is required.");
        BigDecimal netBookValue = asset.netBookValue();
        List<JournalPostingLine> lines = new ArrayList<>();
        if (asset.getAccumulatedDepreciation().signum() > 0) {
            lines.add(JournalPostingLine.debit(asset.getAccumulatedDepreciationAccountId(), asset.getAccumulatedDepreciation(), "Hapus akumulasi " + asset.getAssetCode()));
        }
        if (netBookValue.signum() > 0) {
            Account lossAccount = findAccount(command.lossAccountId());
            requireType(lossAccount, AccountType.EXPENSE, "FIXED_ASSET_DISPOSAL_LOSS_ACCOUNT_INVALID", "Fixed asset disposal loss account must be expense.");
            lines.add(JournalPostingLine.debit(lossAccount.getId(), netBookValue, "Rugi pelepasan aset " + asset.getAssetCode()));
        }
        lines.add(JournalPostingLine.credit(asset.getAssetAccountId(), asset.getAcquisitionCost(), "Hapus aset " + asset.getAssetCode()));
        JournalEntry journal = postSourceJournal(
                sourceJournalNumber("FA-DISP-", asset.getAssetCode()),
                period,
                "Dispose fixed asset " + asset.getAssetCode(),
                "FIXED_ASSET_DISPOSAL",
                asset.getId(),
                asset.getAssetCode(),
                lines,
                actor,
                reason
        );
        asset.dispose(journal.getId(), reason);
        FixedAsset saved = fixedAssetRepository.save(asset);
        auditTrailService.record(actor, "ACCOUNTING", "DISPOSE_FIXED_ASSET", saved.getId().toString(), reason);
        return saved;
    }

    @Transactional
    public JournalEntry reverseJournal(UUID journalId, ReverseJournalCommand command, String actor) {
        JournalEntry original = journalEntryRepository.findWithLinesById(requireUuid(journalId, "JOURNAL_ID_REQUIRED", "Journal id is required."))
                .orElseThrow(() -> new BusinessException("JOURNAL_NOT_FOUND", "Journal entry was not found."));
        if (original.getStatus() != JournalStatus.POSTED) {
            throw new BusinessException("JOURNAL_REVERSAL_STATUS_INVALID", "Only posted journal can be reversed.");
        }
        if (journalEntryRepository.existsBySourceModuleAndSourceRecordId("JOURNAL_REVERSAL", original.getId())) {
            throw new BusinessException("JOURNAL_REVERSAL_DUPLICATE", "Journal already has a reversal journal.");
        }
        String period = normalizePeriod(command.period(), "JOURNAL_REVERSAL_PERIOD_REQUIRED", "Journal reversal period is required.");
        List<JournalPostingLine> lines = original.getLines().stream()
                .map(line -> line.getDebit().signum() > 0
                        ? JournalPostingLine.credit(line.getAccountId(), line.getDebit(), "Reversal debit " + original.getJournalNumber())
                        : JournalPostingLine.debit(line.getAccountId(), line.getCredit(), "Reversal credit " + original.getJournalNumber()))
                .toList();
        return postSourceJournal(
                sourceJournalNumber("JRN-REV-", original.getJournalNumber()),
                period,
                "Reverse journal " + original.getJournalNumber(),
                "JOURNAL_REVERSAL",
                original.getId(),
                original.getJournalNumber(),
                lines,
                actor,
                command.reason()
        );
    }

    @Transactional
    public JournalEntry postOpeningBalance(PostOpeningBalanceCommand command, String actor) {
        String period = normalizePeriod(command.period(), "OPENING_BALANCE_PERIOD_REQUIRED", "Opening balance period is required.");
        UUID sourceRecordId = deterministicUuid("OPENING_BALANCE:" + period);
        if (journalEntryRepository.existsBySourceModuleAndSourceRecordId("OPENING_BALANCE", sourceRecordId)) {
            throw new BusinessException("OPENING_BALANCE_DUPLICATE", "Opening balance already exists for this period.");
        }
        List<JournalPostingLine> lines = command.lines().stream()
                .map(line -> new JournalPostingLine(line.accountId(), normalizeMoney(line.debit()), normalizeMoney(line.credit()), line.description()))
                .toList();
        return postSourceJournal(
                sourceJournalNumber("OB-", period),
                period,
                "Opening balance " + period,
                "OPENING_BALANCE",
                sourceRecordId,
                period,
                lines,
                actor,
                command.reason()
        );
    }

    @Transactional
    public JournalEntry postClosingEntries(PostClosingEntryCommand command, String actor) {
        String period = normalizePeriod(command.period(), "CLOSING_ENTRY_PERIOD_REQUIRED", "Closing entry period is required.");
        UUID sourceRecordId = deterministicUuid("CLOSING_ENTRY:" + period);
        if (journalEntryRepository.existsBySourceModuleAndSourceRecordId("CLOSING_ENTRY", sourceRecordId)) {
            throw new BusinessException("CLOSING_ENTRY_DUPLICATE", "Closing entry already exists for this period.");
        }
        Account retainedEarnings = findAccount(command.retainedEarningsAccountId());
        requireType(retainedEarnings, AccountType.EQUITY, "CLOSING_ENTRY_RETAINED_EARNINGS_INVALID", "Retained earnings account must be equity.");
        YearMonth yearMonth = YearMonth.parse(period, PERIOD_FORMATTER);
        List<LedgerEntry> entries = ledgerEntryRepository.findByPostingDateBetween(
                yearMonth.atDay(1),
                yearMonth.atEndOfMonth()
        );
        Map<UUID, Account> accounts = new HashMap<>();
        accountRepository.findAllById(entries.stream().map(LedgerEntry::getAccountId).distinct().toList())
                .forEach(account -> accounts.put(account.getId(), account));
        Map<UUID, BigDecimal> netByAccount = new HashMap<>();
        for (LedgerEntry entry : entries) {
            Account account = accounts.get(entry.getAccountId());
            if (account == null || (account.getType() != AccountType.REVENUE && account.getType() != AccountType.EXPENSE)) {
                continue;
            }
            BigDecimal netDebit = entry.getDebit().subtract(entry.getCredit()).setScale(2, RoundingMode.HALF_UP);
            netByAccount.merge(account.getId(), netDebit, BigDecimal::add);
        }
        List<JournalPostingLine> lines = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> entry : netByAccount.entrySet()) {
            Account account = accounts.get(entry.getKey());
            BigDecimal netDebit = entry.getValue().setScale(2, RoundingMode.HALF_UP);
            if (netDebit.signum() == 0) {
                continue;
            }
            if (account.getType() == AccountType.REVENUE) {
                BigDecimal creditBalance = netDebit.negate();
                if (creditBalance.signum() > 0) {
                    lines.add(JournalPostingLine.debit(account.getId(), creditBalance, "Tutup pendapatan " + account.getCode()));
                    lines.add(JournalPostingLine.credit(retainedEarnings.getId(), creditBalance, "Ikhtisar laba rugi " + period));
                }
            }
            if (account.getType() == AccountType.EXPENSE && netDebit.signum() > 0) {
                lines.add(JournalPostingLine.debit(retainedEarnings.getId(), netDebit, "Ikhtisar laba rugi " + period));
                lines.add(JournalPostingLine.credit(account.getId(), netDebit, "Tutup beban " + account.getCode()));
            }
        }
        if (lines.isEmpty()) {
            throw new BusinessException("CLOSING_ENTRY_EMPTY", "No revenue or expense ledger balance was found for closing.");
        }
        return postSourceJournal(
                sourceJournalNumber("CL-", period),
                period,
                "Closing entries " + period,
                "CLOSING_ENTRY",
                sourceRecordId,
                period,
                lines,
                actor,
                command.reason()
        );
    }

    @Transactional
    public JournalEntry postReceivableAllowance(PostReceivableAllowanceCommand command, String actor) {
        String period = normalizePeriod(command.period(), "RECEIVABLE_ALLOWANCE_PERIOD_REQUIRED", "Receivable allowance period is required.");
        BigDecimal amount = requirePositive(command.amount(), "RECEIVABLE_ALLOWANCE_AMOUNT_REQUIRED", "Receivable allowance amount is required.");
        Account expenseAccount = findAccount(command.expenseAccountId());
        Account allowanceAccount = findAccount(command.allowanceAccountId());
        requireType(expenseAccount, AccountType.EXPENSE, "RECEIVABLE_ALLOWANCE_EXPENSE_ACCOUNT_INVALID", "Allowance expense account must be expense.");
        requireOneOf(
                allowanceAccount,
                "RECEIVABLE_ALLOWANCE_ACCOUNT_INVALID",
                "Allowance account must be an asset or liability account.",
                AccountType.ASSET,
                AccountType.LIABILITY
        );
        UUID sourceRecordId = deterministicUuid("RECEIVABLE_ALLOWANCE:" + period);
        return postSourceJournal(
                sourceJournalNumber("REC-ALLOW-", period),
                period,
                "Receivable allowance " + period,
                "RECEIVABLE_ALLOWANCE",
                sourceRecordId,
                period,
                List.of(
                        JournalPostingLine.debit(expenseAccount.getId(), amount, "Beban penyisihan piutang " + period),
                        JournalPostingLine.credit(allowanceAccount.getId(), amount, "Cadangan kerugian piutang " + period)
                ),
                actor,
                command.reason()
        );
    }

    private JournalEntry postSourceJournal(
            String journalNumber,
            String periodValue,
            String description,
            String sourceModule,
            UUID sourceRecordId,
            String sourceDocumentNumber,
            List<JournalPostingLine> lines,
            String actor,
            String reason
    ) {
        if (lines == null || lines.size() < 2) {
            throw new BusinessException("JOURNAL_LINES_INSUFFICIENT", "Journal needs at least two lines.");
        }
        AccountingPeriod period = accountingPeriodRepository.findByPeriod(periodValue)
                .orElseThrow(() -> new BusinessException("ACCOUNTING_PERIOD_NOT_FOUND", "Accounting period was not found."));
        journalEntryRepository.findByJournalNumber(journalNumber).ifPresent(existing -> {
            throw new BusinessException("JOURNAL_NUMBER_DUPLICATE", "Journal number already exists.");
        });
        if (journalEntryRepository.existsBySourceModuleAndSourceRecordId(sourceModule, sourceRecordId)) {
            throw new BusinessException("JOURNAL_SOURCE_DUPLICATE", "A journal already exists for this source document.");
        }
        JournalEntry journal = JournalEntry.draftFromSource(
                journalNumber,
                period.getId(),
                description,
                sourceModule,
                sourceRecordId,
                sourceDocumentNumber
        );
        for (JournalPostingLine line : lines) {
            findAccount(line.accountId());
            journal.addLine(line.accountId(), normalizeMoney(line.debit()), normalizeMoney(line.credit()), line.description());
        }
        JournalEntry saved = journalEntryRepository.save(journal);
        postingService.post(saved, period, actor, reason);
        return saved;
    }

    private Account findAccount(UUID accountId) {
        if (accountId == null) {
            throw new BusinessException("JOURNAL_ACCOUNT_REQUIRED", "Journal account is required.");
        }
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException("JOURNAL_ACCOUNT_NOT_FOUND", "Journal account was not found."));
    }

    private static void requireType(Account account, AccountType expected, String code, String message) {
        if (account.getType() != expected) {
            throw new BusinessException(code, message);
        }
    }

    private static void requireOneOf(Account account, String code, String message, AccountType first, AccountType second) {
        if (account.getType() != first && account.getType() != second) {
            throw new BusinessException(code, message);
        }
    }

    private static Pageable pageable(int page, int size, Sort sort) {
        if (page < 0) {
            throw new BusinessException("PAGE_INVALID", "Page must be zero or greater.");
        }
        if (size < 1) {
            throw new BusinessException("PAGE_SIZE_INVALID", "Page size must be at least one.");
        }
        return PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), sort);
    }

    private static String normalizeOptionalPeriod(String period) {
        if (period == null || period.isBlank()) {
            return null;
        }
        return normalizePeriod(period, "PERIOD_REQUIRED", "Period is required.");
    }

    private static String normalizePeriod(String period, String code, String message) {
        String normalized = requireNormalize(period, code, message);
        YearMonth.parse(normalized, PERIOD_FORMATTER);
        return normalized;
    }

    private static UUID requireUuid(UUID value, String code, String message) {
        if (value == null) {
            throw new BusinessException(code, message);
        }
        return value;
    }

    private static BigDecimal requirePositive(BigDecimal value, String code, String message) {
        BigDecimal normalized = normalizeMoney(value);
        if (normalized.signum() <= 0) {
            throw new BusinessException(code, message);
        }
        return normalized;
    }

    private static BigDecimal normalizeMoney(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String requireNormalize(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    private static UUID deterministicUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sourceJournalNumber(String prefix, String documentNumber) {
        String normalized = prefix + documentNumber.replace(" ", "-");
        if (normalized.length() <= 64) {
            return normalized;
        }
        String digest = sha256(normalized).substring(0, 12);
        return normalized.substring(0, 51) + "-" + digest;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record JournalPostingLine(UUID accountId, BigDecimal debit, BigDecimal credit, String description) {
        private static JournalPostingLine debit(UUID accountId, BigDecimal amount, String description) {
            return new JournalPostingLine(accountId, amount, BigDecimal.ZERO, description);
        }

        private static JournalPostingLine credit(UUID accountId, BigDecimal amount, String description) {
            return new JournalPostingLine(accountId, BigDecimal.ZERO, amount, description);
        }
    }

    public record CreateSupplierCommand(String code, String name, String contactName, String phoneNumber, String reason) {
    }

    public record RecordPayableCommand(
            UUID supplierId,
            String payableNumber,
            String supplierReference,
            String period,
            BigDecimal amount,
            String description,
            UUID debitAccountId,
            UUID payableAccountId,
            String reason
    ) {
    }

    public record SettlePayableCommand(UUID payableAccountId, UUID cashAccountId, String reason) {
    }

    public record RegisterFixedAssetCommand(
            String assetCode,
            String name,
            String period,
            LocalDate acquisitionDate,
            BigDecimal acquisitionCost,
            BigDecimal salvageValue,
            int usefulLifeMonths,
            FixedAssetDepreciationMethod depreciationMethod,
            UUID assetAccountId,
            UUID creditAccountId,
            UUID accumulatedDepreciationAccountId,
            UUID depreciationExpenseAccountId,
            String reason
    ) {
    }

    public record PostAssetDepreciationCommand(String period, BigDecimal amount, String reason) {
    }

    public record DisposeFixedAssetCommand(String period, UUID lossAccountId, String reason) {
    }

    public record ReverseJournalCommand(String period, String reason) {
    }

    public record PostOpeningBalanceCommand(String period, List<PostOpeningBalanceLineCommand> lines, String reason) {
    }

    public record PostOpeningBalanceLineCommand(UUID accountId, BigDecimal debit, BigDecimal credit, String description) {
    }

    public record PostClosingEntryCommand(String period, UUID retainedEarningsAccountId, String reason) {
    }

    public record PostReceivableAllowanceCommand(
            String period,
            BigDecimal amount,
            UUID expenseAccountId,
            UUID allowanceAccountId,
            String reason
    ) {
    }
}
