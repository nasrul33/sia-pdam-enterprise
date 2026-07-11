package id.pdam.sia.accounting.application;

import id.pdam.sia.accounting.domain.Account;
import id.pdam.sia.accounting.domain.AccountType;
import id.pdam.sia.accounting.domain.AccountingPeriod;
import id.pdam.sia.accounting.domain.JournalEntry;
import id.pdam.sia.accounting.domain.JournalLine;
import id.pdam.sia.accounting.domain.JournalStatus;
import id.pdam.sia.accounting.repository.AccountRepository;
import id.pdam.sia.accounting.repository.AccountingPeriodRepository;
import id.pdam.sia.accounting.repository.JournalEntryRepository;
import id.pdam.sia.accounting.web.CreateAccountRequest;
import id.pdam.sia.accounting.web.CreateAccountingPeriodRequest;
import id.pdam.sia.accounting.web.CreateJournalRequest;
import id.pdam.sia.accounting.web.JournalLineRequest;
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
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

@Service
public class AccountingApplicationService {
    private static final int MAX_PAGE_SIZE = 100;

    private final AccountRepository accountRepository;
    private final AccountingPeriodRepository accountingPeriodRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final PostingService postingService;
    private final AuditTrailService auditTrailService;
    private final PreCloseChecklistService preCloseChecklistService;

    public AccountingApplicationService(
            AccountRepository accountRepository,
            AccountingPeriodRepository accountingPeriodRepository,
            JournalEntryRepository journalEntryRepository,
            PostingService postingService,
            AuditTrailService auditTrailService,
            PreCloseChecklistService preCloseChecklistService
    ) {
        this.accountRepository = accountRepository;
        this.accountingPeriodRepository = accountingPeriodRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.postingService = postingService;
        this.auditTrailService = auditTrailService;
        this.preCloseChecklistService = preCloseChecklistService;
    }

    @Transactional(readOnly = true)
    public Page<Account> listAccounts(int page, int size) {
        return accountRepository.findAll(pageable(page, size, Sort.by("code").ascending()));
    }

    @Transactional
    public Account createAccount(CreateAccountRequest request, String actor) {
        String code = normalize(request.code());
        accountRepository.findByCode(code).ifPresent(existing -> {
            throw new BusinessException("ACCOUNT_CODE_DUPLICATE", "Account code already exists.");
        });

        Account account = accountRepository.save(new Account(code, request.name(), request.type()));
        auditTrailService.record(actor, "ACCOUNTING", "CREATE_ACCOUNT", account.getId().toString(), request.reason());
        return account;
    }

    @Transactional(readOnly = true)
    public Page<AccountingPeriod> listAccountingPeriods(int page, int size) {
        return accountingPeriodRepository.findAll(pageable(page, size, Sort.by("period").descending()));
    }

    @Transactional
    public AccountingPeriod createAccountingPeriod(CreateAccountingPeriodRequest request, String actor) {
        accountingPeriodRepository.findByPeriod(request.period()).ifPresent(existing -> {
            throw new BusinessException("ACCOUNTING_PERIOD_DUPLICATE", "Accounting period already exists.");
        });

        AccountingPeriod period = accountingPeriodRepository.save(new AccountingPeriod(request.period()));
        auditTrailService.record(actor, "ACCOUNTING", "CREATE_ACCOUNTING_PERIOD", period.getId().toString(), request.reason());
        return period;
    }

    @Transactional
    public AccountingPeriod startClosingReview(UUID periodId, String reason, String actor) {
        AccountingPeriod period = findPeriod(periodId);
        preCloseChecklistService.requireClear(period);
        period.startClosingReview();
        auditTrailService.record(actor, "ACCOUNTING", "START_PERIOD_CLOSING_REVIEW", period.getId().toString(), reason);
        return period;
    }

    @Transactional(readOnly = true)
    public PreCloseChecklist getPreCloseChecklist(UUID periodId) {
        return preCloseChecklistService.evaluate(findPeriod(periodId));
    }

    @Transactional
    public AccountingPeriod lockPeriod(UUID periodId, String reason, String actor) {
        AccountingPeriod period = findPeriod(periodId);
        period.lock();
        auditTrailService.record(actor, "ACCOUNTING", "LOCK_ACCOUNTING_PERIOD", period.getId().toString(), reason);
        return period;
    }

    @Transactional(readOnly = true)
    public Page<JournalEntry> listJournals(UUID accountingPeriodId, JournalStatus status, int page, int size) {
        Pageable pageable = pageable(page, size, Sort.by("createdAt").descending());
        if (accountingPeriodId != null && status != null) {
            return journalEntryRepository.findByAccountingPeriodIdAndStatus(accountingPeriodId, status, pageable);
        }
        if (accountingPeriodId != null) {
            return journalEntryRepository.findByAccountingPeriodId(accountingPeriodId, pageable);
        }
        if (status != null) {
            return journalEntryRepository.findByStatus(status, pageable);
        }
        return journalEntryRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public JournalEntry getJournal(UUID journalId) {
        return journalEntryRepository.findWithLinesById(journalId)
                .orElseThrow(() -> new BusinessException("JOURNAL_NOT_FOUND", "Journal entry was not found."));
    }

    @Transactional
    public JournalEntry createJournal(CreateJournalRequest request, String actor) {
        journalEntryRepository.findByJournalNumber(request.journalNumber()).ifPresent(existing -> {
            throw new BusinessException("JOURNAL_NUMBER_DUPLICATE", "Journal number already exists.");
        });

        AccountingPeriod period = findPeriod(request.accountingPeriodId());
        if (!period.allowsPosting()) {
            throw new BusinessException("JOURNAL_PERIOD_LOCKED", "Journal cannot be created in a locked period.");
        }

        Set<UUID> accountIds = new HashSet<>();
        JournalEntry journal = JournalEntry.draft(
                normalize(request.journalNumber()),
                request.accountingPeriodId(),
                request.description()
        );

        for (JournalLineRequest line : request.lines()) {
            ensureAccountExists(line.accountId());
            accountIds.add(line.accountId());
            journal.addLine(line.accountId(), line.debit(), line.credit(), line.description());
        }

        if (accountIds.size() < 2) {
            throw new BusinessException(
                    "JOURNAL_ACCOUNTS_INSUFFICIENT",
                    "Journal must use at least two accounts for control review."
            );
        }

        JournalEntry saved = journalEntryRepository.save(journal);
        auditTrailService.record(actor, "ACCOUNTING", "CREATE_DRAFT_JOURNAL", saved.getId().toString(), request.reason());
        return saved;
    }

    @Transactional
    public JournalEntry postBillingInvoice(BillingInvoicePostingCommand command, String actor) {
        if (command == null) {
            throw new BusinessException("BILLING_POSTING_COMMAND_REQUIRED", "Billing invoice posting command is required.");
        }
        String invoiceNumber = requireNormalize(
                command.invoiceNumber(),
                "BILLING_POSTING_INVOICE_NUMBER_REQUIRED",
                "Billing invoice number is required."
        );
        if (command.invoiceId() == null) {
            throw new BusinessException("BILLING_POSTING_INVOICE_REQUIRED", "Billing invoice id is required.");
        }
        String periodValue = normalizeAccountingPeriod(command.period());
        BigDecimal amount = requirePositiveAmount(command.amount());
        BigDecimal waterRevenueAmount = requireNonNegativeAmount(
                command.waterRevenueAmount(),
                "BILLING_WATER_REVENUE_AMOUNT_REQUIRED",
                "Billing water revenue amount is required."
        );
        BigDecimal nonAirRevenueAmount = requireNonNegativeAmount(
                command.nonAirRevenueAmount(),
                "BILLING_NON_AIR_REVENUE_AMOUNT_REQUIRED",
                "Billing non-air revenue amount is required."
        );
        BigDecimal penaltyRevenueAmount = requireNonNegativeAmount(
                command.penaltyRevenueAmount(),
                "BILLING_PENALTY_REVENUE_AMOUNT_REQUIRED",
                "Billing penalty revenue amount is required."
        );
        BigDecimal componentTotal = waterRevenueAmount
                .add(nonAirRevenueAmount)
                .add(penaltyRevenueAmount);
        if (componentTotal.compareTo(amount) != 0) {
            throw new BusinessException(
                    "BILLING_POSTING_COMPONENT_TOTAL_MISMATCH",
                    "Billing revenue components must equal the invoice posting amount."
            );
        }
        if (journalEntryRepository.existsBySourceModuleAndSourceRecordId("BILLING", command.invoiceId())) {
            throw new BusinessException(
                    "BILLING_INVOICE_JOURNAL_DUPLICATE",
                    "Invoice already has a billing journal."
            );
        }
        AccountingPeriod period = accountingPeriodRepository.findByPeriod(periodValue)
                .orElseThrow(() -> new BusinessException("ACCOUNTING_PERIOD_NOT_FOUND", "Accounting period was not found."));
        Account receivableAccount = findAccount(command.receivableAccountId());
        requireAccountType(receivableAccount, AccountType.ASSET, "BILLING_RECEIVABLE_ACCOUNT_INVALID", "Billing receivable account must be an asset account.");
        Account waterRevenueAccount = findRevenueAccountWhenRequired(
                command.revenueAccountId(),
                waterRevenueAmount,
                "BILLING_REVENUE_ACCOUNT_REQUIRED",
                "Billing water revenue account is required.",
                "BILLING_REVENUE_ACCOUNT_INVALID",
                "Billing water revenue account must be a revenue account."
        );
        Account nonAirRevenueAccount = findRevenueAccountWhenRequired(
                command.nonAirRevenueAccountId(),
                nonAirRevenueAmount,
                "BILLING_NON_AIR_REVENUE_ACCOUNT_REQUIRED",
                "Billing non-air revenue account is required.",
                "BILLING_NON_AIR_REVENUE_ACCOUNT_INVALID",
                "Billing non-air revenue account must be a revenue account."
        );
        Account penaltyRevenueAccount = findRevenueAccountWhenRequired(
                command.penaltyRevenueAccountId(),
                penaltyRevenueAmount,
                "BILLING_PENALTY_REVENUE_ACCOUNT_REQUIRED",
                "Billing penalty revenue account is required.",
                "BILLING_PENALTY_REVENUE_ACCOUNT_INVALID",
                "Billing penalty revenue account must be a revenue account."
        );

        String journalNumber = billingJournalNumber(invoiceNumber);
        journalEntryRepository.findByJournalNumber(journalNumber).ifPresent(existing -> {
            throw new BusinessException("JOURNAL_NUMBER_DUPLICATE", "Journal number already exists.");
        });

        JournalEntry journal = JournalEntry.draftFromSource(
                journalNumber,
                period.getId(),
                "Issue invoice " + invoiceNumber,
                "BILLING",
                command.invoiceId(),
                invoiceNumber
        );
        journal.addLine(
                receivableAccount.getId(),
                amount,
                BigDecimal.ZERO,
                "Piutang tagihan " + invoiceNumber
        );
        addRevenueLine(journal, waterRevenueAccount, waterRevenueAmount, "Pendapatan air " + invoiceNumber);
        addRevenueLine(journal, nonAirRevenueAccount, nonAirRevenueAmount, "Pendapatan non-air " + invoiceNumber);
        addRevenueLine(journal, penaltyRevenueAccount, penaltyRevenueAmount, "Pendapatan denda " + invoiceNumber);

        JournalEntry saved = journalEntryRepository.save(journal);
        postingService.post(saved, period, actor, command.reason());
        return saved;
    }

    @Transactional
    public JournalEntry postBillingInvoiceVoid(BillingInvoiceVoidPostingCommand command, String actor) {
        if (command == null) {
            throw new BusinessException("BILLING_VOID_POSTING_COMMAND_REQUIRED", "Billing invoice void posting command is required.");
        }
        String invoiceNumber = requireNormalize(
                command.invoiceNumber(),
                "BILLING_VOID_INVOICE_NUMBER_REQUIRED",
                "Billing invoice number is required."
        );
        if (command.invoiceId() == null) {
            throw new BusinessException("BILLING_VOID_INVOICE_REQUIRED", "Billing invoice id is required.");
        }
        if (command.originalJournalEntryId() == null) {
            throw new BusinessException("BILLING_VOID_ORIGINAL_JOURNAL_REQUIRED", "Original billing journal is required.");
        }
        String periodValue = normalizeAccountingPeriod(command.period());
        String reason = requireNormalize(command.reason(), "BILLING_VOID_REASON_REQUIRED", "Billing invoice void reason is required.");
        if (journalEntryRepository.existsBySourceModuleAndSourceRecordId("BILLING_VOID", command.invoiceId())) {
            throw new BusinessException(
                    "BILLING_VOID_JOURNAL_DUPLICATE",
                    "Invoice already has a billing void journal."
            );
        }

        AccountingPeriod period = accountingPeriodRepository.findByPeriod(periodValue)
                .orElseThrow(() -> new BusinessException("ACCOUNTING_PERIOD_NOT_FOUND", "Accounting period was not found."));
        JournalEntry originalJournal = journalEntryRepository.findWithLinesById(command.originalJournalEntryId())
                .orElseThrow(() -> new BusinessException("BILLING_VOID_ORIGINAL_JOURNAL_NOT_FOUND", "Original billing journal was not found."));
        if (originalJournal.getStatus() != JournalStatus.POSTED) {
            throw new BusinessException("BILLING_VOID_ORIGINAL_JOURNAL_NOT_POSTED", "Original billing journal must be posted.");
        }
        if (!"BILLING".equals(originalJournal.getSourceModule()) || !command.invoiceId().equals(originalJournal.getSourceRecordId())) {
            throw new BusinessException("BILLING_VOID_SOURCE_MISMATCH", "Original journal does not belong to this invoice.");
        }

        String journalNumber = billingVoidJournalNumber(invoiceNumber);
        journalEntryRepository.findByJournalNumber(journalNumber).ifPresent(existing -> {
            throw new BusinessException("JOURNAL_NUMBER_DUPLICATE", "Journal number already exists.");
        });

        JournalEntry journal = JournalEntry.draftFromSource(
                journalNumber,
                period.getId(),
                "Void invoice " + invoiceNumber,
                "BILLING_VOID",
                command.invoiceId(),
                invoiceNumber
        );
        for (JournalLine originalLine : originalJournal.getLines()) {
            if (originalLine.getDebit().signum() > 0) {
                journal.addLine(
                        originalLine.getAccountId(),
                        BigDecimal.ZERO,
                        originalLine.getDebit(),
                        "Pembalik debit " + invoiceNumber
                );
            } else {
                journal.addLine(
                        originalLine.getAccountId(),
                        originalLine.getCredit(),
                        BigDecimal.ZERO,
                        "Pembalik kredit " + invoiceNumber
                );
            }
        }

        JournalEntry saved = journalEntryRepository.save(journal);
        postingService.post(saved, period, actor, reason);
        return saved;
    }

    @Transactional
    public JournalEntry postPaymentSettlement(PaymentSettlementPostingCommand command, String actor) {
        if (command == null) {
            throw new BusinessException("PAYMENT_POSTING_COMMAND_REQUIRED", "Payment settlement posting command is required.");
        }
        String paymentNumber = requireNormalize(
                command.paymentNumber(),
                "PAYMENT_POSTING_NUMBER_REQUIRED",
                "Payment number is required."
        );
        if (command.paymentId() == null) {
            throw new BusinessException("PAYMENT_POSTING_PAYMENT_REQUIRED", "Payment id is required.");
        }
        String periodValue = normalizeAccountingPeriod(command.period());
        BigDecimal amount = requirePositiveAmount(command.amount(), "PAYMENT_POSTING_AMOUNT_REQUIRED", "Payment posting amount is required.");
        if (journalEntryRepository.existsBySourceModuleAndSourceRecordId("PAYMENT", command.paymentId())) {
            throw new BusinessException(
                    "PAYMENT_JOURNAL_DUPLICATE",
                    "Payment already has a settlement journal."
            );
        }
        if (command.cashAccountId() != null && command.cashAccountId().equals(command.receivableAccountId())) {
            throw new BusinessException(
                    "PAYMENT_POSTING_ACCOUNT_DUPLICATE",
                    "Cash/bank account and receivable account must be different."
            );
        }

        AccountingPeriod period = accountingPeriodRepository.findByPeriod(periodValue)
                .orElseThrow(() -> new BusinessException("ACCOUNTING_PERIOD_NOT_FOUND", "Accounting period was not found."));
        Account cashAccount = findAccount(command.cashAccountId());
        Account receivableAccount = findAccount(command.receivableAccountId());
        requireAccountType(cashAccount, AccountType.ASSET, "PAYMENT_CASH_ACCOUNT_INVALID", "Payment cash or bank account must be an asset account.");
        requireAccountType(receivableAccount, AccountType.ASSET, "PAYMENT_RECEIVABLE_ACCOUNT_INVALID", "Payment receivable account must be an asset account.");

        String journalNumber = paymentJournalNumber(paymentNumber);
        journalEntryRepository.findByJournalNumber(journalNumber).ifPresent(existing -> {
            throw new BusinessException("JOURNAL_NUMBER_DUPLICATE", "Journal number already exists.");
        });

        JournalEntry journal = JournalEntry.draftFromSource(
                journalNumber,
                period.getId(),
                "Settle payment " + paymentNumber,
                "PAYMENT",
                command.paymentId(),
                paymentNumber
        );
        journal.addLine(
                cashAccount.getId(),
                amount,
                BigDecimal.ZERO,
                "Kas/bank pembayaran " + paymentNumber
        );
        journal.addLine(
                receivableAccount.getId(),
                BigDecimal.ZERO,
                amount,
                "Pelunasan piutang " + paymentNumber
        );

        JournalEntry saved = journalEntryRepository.save(journal);
        postingService.post(saved, period, actor, command.reason());
        return saved;
    }

    @Transactional
    public JournalEntry postPaymentReversal(PaymentReversalPostingCommand command, String actor) {
        if (command == null) {
            throw new BusinessException("PAYMENT_REVERSAL_POSTING_COMMAND_REQUIRED", "Payment reversal posting command is required.");
        }
        String paymentNumber = requireNormalize(
                command.paymentNumber(),
                "PAYMENT_REVERSAL_POSTING_NUMBER_REQUIRED",
                "Payment number is required."
        );
        if (command.paymentId() == null) {
            throw new BusinessException("PAYMENT_REVERSAL_POSTING_PAYMENT_REQUIRED", "Payment id is required.");
        }
        String periodValue = normalizeAccountingPeriod(command.period());
        BigDecimal amount = requirePositiveAmount(command.amount(), "PAYMENT_REVERSAL_POSTING_AMOUNT_REQUIRED", "Payment reversal posting amount is required.");
        if (journalEntryRepository.existsBySourceModuleAndSourceRecordId("PAYMENT_REVERSAL", command.paymentId())) {
            throw new BusinessException(
                    "PAYMENT_REVERSAL_JOURNAL_DUPLICATE",
                    "Payment already has a reversal journal."
            );
        }
        if (command.cashAccountId() != null && command.cashAccountId().equals(command.receivableAccountId())) {
            throw new BusinessException(
                    "PAYMENT_REVERSAL_ACCOUNT_DUPLICATE",
                    "Cash/bank account and receivable account must be different."
            );
        }

        AccountingPeriod period = accountingPeriodRepository.findByPeriod(periodValue)
                .orElseThrow(() -> new BusinessException("ACCOUNTING_PERIOD_NOT_FOUND", "Accounting period was not found."));
        Account cashAccount = findAccount(command.cashAccountId());
        Account receivableAccount = findAccount(command.receivableAccountId());
        requireAccountType(cashAccount, AccountType.ASSET, "PAYMENT_CASH_ACCOUNT_INVALID", "Payment cash or bank account must be an asset account.");
        requireAccountType(receivableAccount, AccountType.ASSET, "PAYMENT_RECEIVABLE_ACCOUNT_INVALID", "Payment receivable account must be an asset account.");

        String journalNumber = paymentReversalJournalNumber(paymentNumber);
        journalEntryRepository.findByJournalNumber(journalNumber).ifPresent(existing -> {
            throw new BusinessException("JOURNAL_NUMBER_DUPLICATE", "Journal number already exists.");
        });

        JournalEntry journal = JournalEntry.draftFromSource(
                journalNumber,
                period.getId(),
                "Reverse payment " + paymentNumber,
                "PAYMENT_REVERSAL",
                command.paymentId(),
                paymentNumber
        );
        journal.addLine(
                receivableAccount.getId(),
                amount,
                BigDecimal.ZERO,
                "Pembalikan piutang " + paymentNumber
        );
        journal.addLine(
                cashAccount.getId(),
                BigDecimal.ZERO,
                amount,
                "Pembalikan kas/bank " + paymentNumber
        );

        JournalEntry saved = journalEntryRepository.save(journal);
        postingService.post(saved, period, actor, command.reason());
        return saved;
    }

    @Transactional
    public JournalEntry postPaymentReconciliationAdjustment(PaymentReconciliationAdjustmentPostingCommand command, String actor) {
        if (command == null) {
            throw new BusinessException(
                    "PAYMENT_RECONCILIATION_ADJUSTMENT_COMMAND_REQUIRED",
                    "Payment reconciliation adjustment posting command is required."
            );
        }
        if (command.reconciliationItemId() == null) {
            throw new BusinessException(
                    "PAYMENT_RECONCILIATION_ADJUSTMENT_ITEM_REQUIRED",
                    "Payment reconciliation item id is required."
            );
        }
        String sessionNumber = requireNormalize(
                command.sessionNumber(),
                "PAYMENT_RECONCILIATION_ADJUSTMENT_SESSION_REQUIRED",
                "Payment reconciliation session number is required."
        );
        if (command.rowNumber() < 1) {
            throw new BusinessException(
                    "PAYMENT_RECONCILIATION_ADJUSTMENT_ROW_INVALID",
                    "Payment reconciliation row number must be greater than zero."
            );
        }
        String periodValue = normalizeAccountingPeriod(command.period());
        BigDecimal amount = requirePositiveAmount(
                command.amount(),
                "PAYMENT_RECONCILIATION_ADJUSTMENT_AMOUNT_REQUIRED",
                "Payment reconciliation adjustment amount is required."
        );
        String reason = requireNormalize(
                command.reason(),
                "PAYMENT_RECONCILIATION_ADJUSTMENT_REASON_REQUIRED",
                "Payment reconciliation adjustment reason is required."
        );
        if (journalEntryRepository.existsBySourceModuleAndSourceRecordId(
                "PAYMENT_RECONCILIATION_ADJUSTMENT",
                command.reconciliationItemId()
        )) {
            throw new BusinessException(
                    "PAYMENT_RECONCILIATION_ADJUSTMENT_JOURNAL_DUPLICATE",
                    "Payment reconciliation item already has an adjustment journal."
            );
        }
        if (command.debitAccountId() != null && command.debitAccountId().equals(command.creditAccountId())) {
            throw new BusinessException(
                    "PAYMENT_RECONCILIATION_ADJUSTMENT_ACCOUNT_DUPLICATE",
                    "Adjustment debit and credit accounts must be different."
            );
        }

        AccountingPeriod period = accountingPeriodRepository.findByPeriod(periodValue)
                .orElseThrow(() -> new BusinessException("ACCOUNTING_PERIOD_NOT_FOUND", "Accounting period was not found."));
        Account debitAccount = findAccount(command.debitAccountId());
        Account creditAccount = findAccount(command.creditAccountId());
        String sourceDocumentNumber = reconciliationAdjustmentDocumentNumber(sessionNumber, command.rowNumber());
        String journalNumber = paymentReconciliationAdjustmentJournalNumber(sourceDocumentNumber);
        journalEntryRepository.findByJournalNumber(journalNumber).ifPresent(existing -> {
            throw new BusinessException("JOURNAL_NUMBER_DUPLICATE", "Journal number already exists.");
        });

        JournalEntry journal = JournalEntry.draftFromSource(
                journalNumber,
                period.getId(),
                "Reconciliation adjustment " + sourceDocumentNumber,
                "PAYMENT_RECONCILIATION_ADJUSTMENT",
                command.reconciliationItemId(),
                sourceDocumentNumber
        );
        journal.addLine(
                debitAccount.getId(),
                amount,
                BigDecimal.ZERO,
                "Debit adjustment " + sourceDocumentNumber
        );
        journal.addLine(
                creditAccount.getId(),
                BigDecimal.ZERO,
                amount,
                "Credit adjustment " + sourceDocumentNumber
        );

        JournalEntry saved = journalEntryRepository.save(journal);
        postingService.post(saved, period, actor, reason);
        return saved;
    }

    @Transactional
    public JournalEntry postJournal(UUID journalId, String reason, String actor) {
        JournalEntry journal = journalEntryRepository.findForPosting(journalId)
                .orElseThrow(() -> new BusinessException("JOURNAL_NOT_FOUND", "Journal entry was not found."));
        AccountingPeriod period = findPeriod(journal.getAccountingPeriodId());
        postingService.post(journal, period, actor, reason);
        return journal;
    }

    private AccountingPeriod findPeriod(UUID periodId) {
        return accountingPeriodRepository.findById(periodId)
                .orElseThrow(() -> new BusinessException("ACCOUNTING_PERIOD_NOT_FOUND", "Accounting period was not found."));
    }

    private void ensureAccountExists(UUID accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new BusinessException("JOURNAL_ACCOUNT_NOT_FOUND", "Journal account was not found.");
        }
    }

    private Account findAccount(UUID accountId) {
        if (accountId == null) {
            throw new BusinessException("JOURNAL_ACCOUNT_REQUIRED", "Journal account is required.");
        }
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException("JOURNAL_ACCOUNT_NOT_FOUND", "Journal account was not found."));
    }

    private static void requireAccountType(Account account, AccountType expectedType, String code, String message) {
        if (account.getType() != expectedType) {
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

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private static String requireNormalize(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    private static String normalizeAccountingPeriod(String period) {
        String normalized = requireNormalize(period, "ACCOUNTING_PERIOD_REQUIRED", "Accounting period is required.");
        if (!normalized.matches("\\d{4}-\\d{2}")) {
            throw new BusinessException("PERIOD_INVALID", "Period must use YYYY-MM format.");
        }
        return normalized;
    }

    private static BigDecimal requirePositiveAmount(BigDecimal amount) {
        return requirePositiveAmount(amount, "BILLING_POSTING_AMOUNT_REQUIRED", "Billing invoice posting amount is required.");
    }

    private static BigDecimal requirePositiveAmount(BigDecimal amount, String code, String message) {
        if (amount == null) {
            throw new BusinessException(code, message);
        }
        BigDecimal normalized = amount.setScale(2, RoundingMode.HALF_UP);
        if (normalized.signum() <= 0) {
            throw new BusinessException("POSTING_AMOUNT_INVALID", "Posting amount must be greater than zero.");
        }
        return normalized;
    }

    private static BigDecimal requireNonNegativeAmount(BigDecimal amount, String code, String message) {
        if (amount == null) {
            throw new BusinessException(code, message);
        }
        BigDecimal normalized = amount.setScale(2, RoundingMode.HALF_UP);
        if (normalized.signum() < 0) {
            throw new BusinessException("POSTING_AMOUNT_INVALID", "Posting amount must not be negative.");
        }
        return normalized;
    }

    private Account findRevenueAccountWhenRequired(
            UUID accountId,
            BigDecimal amount,
            String requiredCode,
            String requiredMessage,
            String invalidCode,
            String invalidMessage
    ) {
        if (amount.signum() == 0) {
            return null;
        }
        if (accountId == null) {
            throw new BusinessException(requiredCode, requiredMessage);
        }
        Account account = findAccount(accountId);
        requireAccountType(account, AccountType.REVENUE, invalidCode, invalidMessage);
        return account;
    }

    private static void addRevenueLine(
            JournalEntry journal,
            Account account,
            BigDecimal amount,
            String description
    ) {
        if (amount.signum() > 0) {
            journal.addLine(account.getId(), BigDecimal.ZERO, amount, description);
        }
    }

    private static String billingJournalNumber(String invoiceNumber) {
        return sourceJournalNumber("BIL-", invoiceNumber);
    }

    private static String billingVoidJournalNumber(String invoiceNumber) {
        return sourceJournalNumber("BIL-VOID-", invoiceNumber);
    }

    private static String paymentJournalNumber(String paymentNumber) {
        return sourceJournalNumber("PMT-", paymentNumber);
    }

    private static String paymentReversalJournalNumber(String paymentNumber) {
        return sourceJournalNumber("REV-", paymentNumber);
    }

    private static String paymentReconciliationAdjustmentJournalNumber(String sourceDocumentNumber) {
        return sourceJournalNumber("REC-ADJ-", sourceDocumentNumber);
    }

    private static String reconciliationAdjustmentDocumentNumber(String sessionNumber, int rowNumber) {
        return sourceJournalNumber("", sessionNumber + "-ROW-" + rowNumber);
    }

    private static String sourceJournalNumber(String prefix, String documentNumber) {
        String journalNumber = prefix + documentNumber;
        if (journalNumber.length() <= 64) {
            return journalNumber;
        }
        String digest = sha256(journalNumber).substring(0, 12);
        return journalNumber.substring(0, 51) + "-" + digest;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
