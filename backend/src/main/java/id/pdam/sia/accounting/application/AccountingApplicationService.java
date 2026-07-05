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

    public AccountingApplicationService(
            AccountRepository accountRepository,
            AccountingPeriodRepository accountingPeriodRepository,
            JournalEntryRepository journalEntryRepository,
            PostingService postingService,
            AuditTrailService auditTrailService
    ) {
        this.accountRepository = accountRepository;
        this.accountingPeriodRepository = accountingPeriodRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.postingService = postingService;
        this.auditTrailService = auditTrailService;
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
        period.startClosingReview();
        auditTrailService.record(actor, "ACCOUNTING", "START_PERIOD_CLOSING_REVIEW", period.getId().toString(), reason);
        return period;
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
        if (journalEntryRepository.existsBySourceModuleAndSourceRecordId("BILLING", command.invoiceId())) {
            throw new BusinessException(
                    "BILLING_INVOICE_JOURNAL_DUPLICATE",
                    "Invoice already has a billing journal."
            );
        }
        AccountingPeriod period = accountingPeriodRepository.findByPeriod(periodValue)
                .orElseThrow(() -> new BusinessException("ACCOUNTING_PERIOD_NOT_FOUND", "Accounting period was not found."));
        Account receivableAccount = findAccount(command.receivableAccountId());
        Account revenueAccount = findAccount(command.revenueAccountId());
        requireAccountType(receivableAccount, AccountType.ASSET, "BILLING_RECEIVABLE_ACCOUNT_INVALID", "Billing receivable account must be an asset account.");
        requireAccountType(revenueAccount, AccountType.REVENUE, "BILLING_REVENUE_ACCOUNT_INVALID", "Billing revenue account must be a revenue account.");

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
        journal.addLine(
                revenueAccount.getId(),
                BigDecimal.ZERO,
                amount,
                "Pendapatan air " + invoiceNumber
        );

        JournalEntry saved = journalEntryRepository.save(journal);
        postingService.post(saved, period, actor, command.reason());
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
        if (amount == null) {
            throw new BusinessException("BILLING_POSTING_AMOUNT_REQUIRED", "Billing invoice posting amount is required.");
        }
        BigDecimal normalized = amount.setScale(2, RoundingMode.HALF_UP);
        if (normalized.signum() <= 0) {
            throw new BusinessException("BILLING_POSTING_AMOUNT_INVALID", "Billing invoice posting amount must be greater than zero.");
        }
        return normalized;
    }

    private static String billingJournalNumber(String invoiceNumber) {
        String journalNumber = "BIL-" + invoiceNumber;
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
