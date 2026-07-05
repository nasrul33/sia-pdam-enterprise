package id.pdam.sia.accounting.domain;

import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "journal_lines")
public class JournalLine extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    private JournalEntry journalEntry;

    @Column(nullable = false)
    private UUID accountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal debit;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal credit;

    @Column(nullable = false, length = 255)
    private String description;

    protected JournalLine() {
    }

    JournalLine(JournalEntry journalEntry, UUID accountId, BigDecimal debit, BigDecimal credit, String description) {
        if (accountId == null) {
            throw new BusinessException("JOURNAL_ACCOUNT_REQUIRED", "Account is required.");
        }
        BigDecimal safeDebit = debit == null ? BigDecimal.ZERO : debit;
        BigDecimal safeCredit = credit == null ? BigDecimal.ZERO : credit;
        if (safeDebit.signum() < 0 || safeCredit.signum() < 0) {
            throw new BusinessException("JOURNAL_NEGATIVE_LINE", "Debit and credit cannot be negative.");
        }
        if (safeDebit.signum() > 0 && safeCredit.signum() > 0) {
            throw new BusinessException("JOURNAL_DOUBLE_SIDED_LINE", "A line cannot have both debit and credit.");
        }
        if (safeDebit.signum() == 0 && safeCredit.signum() == 0) {
            throw new BusinessException("JOURNAL_ZERO_LINE", "A line must have debit or credit.");
        }
        this.journalEntry = journalEntry;
        this.accountId = accountId;
        this.debit = safeDebit.setScale(2);
        this.credit = safeCredit.setScale(2);
        this.description = description == null ? "" : description;
    }

    public BigDecimal getDebit() {
        return debit;
    }

    public BigDecimal getCredit() {
        return credit;
    }
}
