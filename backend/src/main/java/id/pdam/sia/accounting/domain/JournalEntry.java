package id.pdam.sia.accounting.domain;

import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.persistence.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "journal_entries")
public class JournalEntry extends BaseEntity {
    @Column(nullable = false, unique = true, length = 64)
    private String journalNumber;

    @Column(nullable = false)
    private UUID accountingPeriodId;

    @Column(nullable = false, length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private JournalStatus status;

    private Instant postedAt;

    @Column(length = 128)
    private String postedBy;

    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JournalLine> lines = new ArrayList<>();

    protected JournalEntry() {
    }

    private JournalEntry(String journalNumber, UUID accountingPeriodId, String description) {
        if (journalNumber == null || journalNumber.isBlank()) {
            throw new BusinessException("JOURNAL_NUMBER_REQUIRED", "Journal number is required.");
        }
        if (accountingPeriodId == null) {
            throw new BusinessException("JOURNAL_PERIOD_REQUIRED", "Accounting period is required.");
        }
        this.journalNumber = journalNumber;
        this.accountingPeriodId = accountingPeriodId;
        this.description = description == null ? "" : description;
        this.status = JournalStatus.DRAFT;
    }

    public static JournalEntry draft(String journalNumber, UUID accountingPeriodId, String description) {
        return new JournalEntry(journalNumber, accountingPeriodId, description);
    }

    public void addLine(UUID accountId, BigDecimal debit, BigDecimal credit, String description) {
        ensureDraft();
        lines.add(new JournalLine(this, accountId, debit, credit, description));
    }

    public BigDecimal totalDebit() {
        return lines.stream().map(JournalLine::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
    }

    public BigDecimal totalCredit() {
        return lines.stream().map(JournalLine::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
    }

    public boolean isBalanced() {
        return totalDebit().compareTo(totalCredit()) == 0 && totalDebit().signum() > 0;
    }

    public void post(AccountingPeriod period, String actor) {
        ensureDraft();
        if (period == null || !period.getId().equals(accountingPeriodId)) {
            throw new BusinessException("JOURNAL_PERIOD_MISMATCH", "Journal period mismatch.");
        }
        if (!period.allowsPosting()) {
            throw new BusinessException("JOURNAL_PERIOD_LOCKED", "Posting is not allowed in this period.");
        }
        if (lines.size() < 2) {
            throw new BusinessException("JOURNAL_LINES_INSUFFICIENT", "Journal needs at least two lines.");
        }
        if (!isBalanced()) {
            throw new BusinessException("JOURNAL_NOT_BALANCED", "Debit must equal credit.");
        }
        status = JournalStatus.POSTED;
        postedAt = Instant.now();
        postedBy = actor;
    }

    public void voidDraft(String reason) {
        ensureDraft();
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("VOID_REASON_REQUIRED", "Void reason is required.");
        }
        status = JournalStatus.VOID;
    }

    private void ensureDraft() {
        if (status != JournalStatus.DRAFT) {
            throw new BusinessException("JOURNAL_NOT_DRAFT", "Only draft journal can be changed.");
        }
    }

    public JournalStatus getStatus() {
        return status;
    }

    public String getJournalNumber() {
        return journalNumber;
    }

    public UUID getAccountingPeriodId() {
        return accountingPeriodId;
    }

    public String getDescription() {
        return description;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public String getPostedBy() {
        return postedBy;
    }

    public List<JournalLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
