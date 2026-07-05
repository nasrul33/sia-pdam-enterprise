package id.pdam.sia.reporting.domain;

import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry extends BaseEntity {
    @Column(nullable = false)
    private UUID journalEntryId;

    @Column(nullable = false, unique = true)
    private UUID journalLineId;

    @Column(nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private LocalDate postingDate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal debit;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal credit;

    @Column(nullable = false, length = 64)
    private String sourceModule;

    private UUID sourceRecordId;

    protected LedgerEntry() {
    }

    public LedgerEntry(
            UUID journalEntryId,
            UUID journalLineId,
            UUID accountId,
            LocalDate postingDate,
            BigDecimal debit,
            BigDecimal credit,
            String sourceModule,
            UUID sourceRecordId
    ) {
        if (journalEntryId == null) {
            throw new BusinessException("LEDGER_JOURNAL_ENTRY_REQUIRED", "Ledger journal entry is required.");
        }
        if (journalLineId == null) {
            throw new BusinessException("LEDGER_JOURNAL_LINE_REQUIRED", "Ledger journal line is required.");
        }
        if (accountId == null) {
            throw new BusinessException("LEDGER_ACCOUNT_REQUIRED", "Ledger account is required.");
        }
        if (postingDate == null) {
            throw new BusinessException("LEDGER_POSTING_DATE_REQUIRED", "Ledger posting date is required.");
        }
        BigDecimal normalizedDebit = normalizeAmount(debit);
        BigDecimal normalizedCredit = normalizeAmount(credit);
        if (normalizedDebit.signum() < 0 || normalizedCredit.signum() < 0) {
            throw new BusinessException("LEDGER_AMOUNT_NEGATIVE", "Ledger debit and credit cannot be negative.");
        }
        if (normalizedDebit.signum() > 0 && normalizedCredit.signum() > 0) {
            throw new BusinessException("LEDGER_DOUBLE_SIDED_LINE", "Ledger line cannot have both debit and credit.");
        }
        if (normalizedDebit.signum() == 0 && normalizedCredit.signum() == 0) {
            throw new BusinessException("LEDGER_ZERO_LINE", "Ledger line must have debit or credit.");
        }
        this.journalEntryId = journalEntryId;
        this.journalLineId = journalLineId;
        this.accountId = accountId;
        this.postingDate = postingDate;
        this.debit = normalizedDebit;
        this.credit = normalizedCredit;
        this.sourceModule = require(sourceModule, "LEDGER_SOURCE_MODULE_REQUIRED", "Ledger source module is required.");
        this.sourceRecordId = sourceRecordId;
    }

    private static BigDecimal normalizeAmount(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String require(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    public UUID getJournalEntryId() {
        return journalEntryId;
    }

    public UUID getJournalLineId() {
        return journalLineId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public LocalDate getPostingDate() {
        return postingDate;
    }

    public BigDecimal getDebit() {
        return debit;
    }

    public BigDecimal getCredit() {
        return credit;
    }

    public String getSourceModule() {
        return sourceModule;
    }

    public UUID getSourceRecordId() {
        return sourceRecordId;
    }
}
