package id.pdam.sia.reporting.application;

import id.pdam.sia.accounting.domain.Account;
import id.pdam.sia.accounting.repository.AccountRepository;
import id.pdam.sia.reporting.domain.LedgerEntry;
import id.pdam.sia.reporting.repository.LedgerEntryRepository;
import id.pdam.sia.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PostedLedgerReportApplicationService {
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountRepository accountRepository;

    public PostedLedgerReportApplicationService(
            LedgerEntryRepository ledgerEntryRepository,
            AccountRepository accountRepository
    ) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public TrialBalanceReport trialBalance(LocalDate fromDate, LocalDate toDate) {
        LocalDate normalizedFromDate = requireDate(fromDate, "REPORT_FROM_DATE_REQUIRED", "Report from date is required.");
        LocalDate normalizedToDate = requireDate(toDate, "REPORT_TO_DATE_REQUIRED", "Report to date is required.");
        if (normalizedFromDate.isAfter(normalizedToDate)) {
            throw new BusinessException("REPORT_DATE_RANGE_INVALID", "Report from date cannot be after to date.");
        }

        List<LedgerEntry> entries = ledgerEntryRepository.findByPostingDateBetween(normalizedFromDate, normalizedToDate);
        Map<UUID, AccountTotals> totalsByAccount = entries.stream()
                .collect(Collectors.toMap(
                        LedgerEntry::getAccountId,
                        entry -> new AccountTotals(entry.getDebit(), entry.getCredit()),
                        AccountTotals::plus,
                        LinkedHashMap::new
                ));
        Map<UUID, Account> accountsById = accountRepository.findAllById(List.copyOf(totalsByAccount.keySet())).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));

        List<TrialBalanceLine> lines = totalsByAccount.entrySet().stream()
                .map(entry -> toLine(entry.getKey(), entry.getValue(), accountsById))
                .sorted(Comparator.comparing(TrialBalanceLine::accountCode))
                .toList();
        BigDecimal totalDebitBalance = lines.stream()
                .map(TrialBalanceLine::debitBalance)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        BigDecimal totalCreditBalance = lines.stream()
                .map(TrialBalanceLine::creditBalance)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);

        return new TrialBalanceReport(
                normalizedFromDate,
                normalizedToDate,
                lines,
                totalDebitBalance,
                totalCreditBalance,
                totalDebitBalance.compareTo(totalCreditBalance) == 0,
                Instant.now()
        );
    }

    private static TrialBalanceLine toLine(UUID accountId, AccountTotals totals, Map<UUID, Account> accountsById) {
        Account account = accountsById.get(accountId);
        if (account == null) {
            throw new BusinessException("REPORT_ACCOUNT_NOT_FOUND", "Ledger account was not found.");
        }
        BigDecimal netDebit = totals.debitTotal().subtract(totals.creditTotal());
        BigDecimal debitBalance = netDebit.signum() > 0 ? netDebit : BigDecimal.ZERO.setScale(2);
        BigDecimal creditBalance = netDebit.signum() < 0 ? netDebit.abs() : BigDecimal.ZERO.setScale(2);
        return new TrialBalanceLine(
                account.getId(),
                account.getCode(),
                account.getName(),
                account.getType(),
                account.getNormalBalance(),
                totals.debitTotal(),
                totals.creditTotal(),
                debitBalance,
                creditBalance
        );
    }

    private static LocalDate requireDate(LocalDate value, String code, String message) {
        if (value == null) {
            throw new BusinessException(code, message);
        }
        return value;
    }

    private record AccountTotals(BigDecimal debitTotal, BigDecimal creditTotal) {
        private AccountTotals plus(AccountTotals other) {
            return new AccountTotals(
                    debitTotal.add(other.debitTotal),
                    creditTotal.add(other.creditTotal)
            );
        }
    }
}
