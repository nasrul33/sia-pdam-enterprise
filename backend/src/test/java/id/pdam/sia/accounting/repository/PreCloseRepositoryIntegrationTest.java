package id.pdam.sia.accounting.repository;

import id.pdam.sia.accounting.domain.Account;
import id.pdam.sia.accounting.domain.AccountType;
import id.pdam.sia.accounting.domain.AccountingPeriod;
import id.pdam.sia.accounting.domain.FixedAsset;
import id.pdam.sia.accounting.domain.FixedAssetDepreciation;
import id.pdam.sia.accounting.domain.FixedAssetDepreciationMethod;
import id.pdam.sia.accounting.domain.FixedAssetStatus;
import id.pdam.sia.accounting.domain.JournalEntry;
import id.pdam.sia.accounting.domain.JournalStatus;
import id.pdam.sia.receivable.domain.ReceivableAgingSnapshot;
import id.pdam.sia.receivable.repository.ReceivableAgingSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
@Transactional
class PreCloseRepositoryIntegrationTest {
    private static final String PERIOD = "2026-07";
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 7, 31);
    private static final Instant PERIOD_END_EXCLUSIVE = Instant.parse("2026-07-31T17:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountingPeriodRepository accountingPeriodRepository;

    @Autowired
    private FixedAssetRepository fixedAssetRepository;

    @Autowired
    private FixedAssetDepreciationRepository depreciationRepository;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private ReceivableAgingSnapshotRepository agingSnapshotRepository;

    @Test
    void depreciationQueryCountsOnlyEligibleAssetsWithRemainingValueAndNoPeriodDepreciation() {
        AccountingPeriod accountingPeriod = accountingPeriodRepository.save(new AccountingPeriod(PERIOD));
        Account assetAccount = accountRepository.save(new Account(uniqueCode("ASSET"), "Fixed asset", AccountType.ASSET));
        Account accumulatedAccount = accountRepository.save(new Account(uniqueCode("ACCUM"), "Accumulated depreciation", AccountType.ASSET));
        Account expenseAccount = accountRepository.save(new Account(uniqueCode("EXPENSE"), "Depreciation expense", AccountType.EXPENSE));

        FixedAsset fullyDepreciated = saveAsset(
                "FULL",
                accountingPeriod,
                assetAccount,
                accumulatedAccount,
                expenseAccount
        );
        fullyDepreciated.postDepreciation(new BigDecimal("900.00"));
        fixedAssetRepository.save(fullyDepreciated);

        FixedAsset disposedAfterPeriod = saveAsset(
                "DISPOSED-AFTER",
                accountingPeriod,
                assetAccount,
                accumulatedAccount,
                expenseAccount
        );
        setField(disposedAfterPeriod, "status", FixedAssetStatus.DISPOSED);
        setField(disposedAfterPeriod, "disposedAt", Instant.parse("2026-08-05T00:00:00Z"));
        fixedAssetRepository.save(disposedAfterPeriod);

        FixedAsset alreadyDepreciated = saveAsset(
                "EXISTING",
                accountingPeriod,
                assetAccount,
                accumulatedAccount,
                expenseAccount
        );
        JournalEntry depreciationJournal = saveJournal(accountingPeriod, "DEPRECIATION");
        depreciationRepository.save(new FixedAssetDepreciation(
                alreadyDepreciated.getId(),
                PERIOD,
                new BigDecimal("75.00"),
                depreciationJournal.getId(),
                "integration-test"
        ));

        long missing = fixedAssetRepository.countMissingDepreciationForPeriod(
                PERIOD,
                PERIOD_END,
                PERIOD_END_EXCLUSIVE,
                FixedAssetStatus.ACTIVE,
                FixedAssetStatus.DISPOSED
        );

        assertThat(missing).isEqualTo(1L);
    }

    @Test
    void allowanceFreshnessQueryRejectsStaleJournalAndAcceptsJournalPostedAfterSnapshot() {
        AccountingPeriod accountingPeriod = accountingPeriodRepository.save(new AccountingPeriod(PERIOD));
        Instant generatedAt = Instant.parse("2026-08-01T01:00:00Z");
        agingSnapshotRepository.save(new ReceivableAgingSnapshot(
                PERIOD,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                generatedAt
        ));

        savePostedAllowanceJournal(
                accountingPeriod,
                "ALLOWANCE-STALE",
                Instant.parse("2026-08-01T00:59:59Z")
        );

        assertThat(countFreshAllowance(accountingPeriod, generatedAt)).isZero();

        savePostedAllowanceJournal(
                accountingPeriod,
                "ALLOWANCE-FRESH",
                Instant.parse("2026-08-01T01:00:00Z")
        );

        assertThat(countFreshAllowance(accountingPeriod, generatedAt)).isEqualTo(1L);
    }

    private FixedAsset saveAsset(
            String codePrefix,
            AccountingPeriod period,
            Account assetAccount,
            Account accumulatedAccount,
            Account expenseAccount
    ) {
        JournalEntry registrationJournal = saveJournal(period, codePrefix + "-REG");
        return fixedAssetRepository.save(new FixedAsset(
                uniqueCode(codePrefix),
                codePrefix,
                LocalDate.of(2025, 1, 1),
                new BigDecimal("1000.00"),
                new BigDecimal("100.00"),
                12,
                FixedAssetDepreciationMethod.STRAIGHT_LINE,
                assetAccount.getId(),
                accumulatedAccount.getId(),
                expenseAccount.getId(),
                registrationJournal.getId()
        ));
    }

    private JournalEntry saveJournal(AccountingPeriod period, String prefix) {
        return journalEntryRepository.save(JournalEntry.draft(uniqueCode(prefix), period.getId(), prefix));
    }

    private void savePostedAllowanceJournal(AccountingPeriod period, String prefix, Instant postedAt) {
        JournalEntry journal = JournalEntry.draftFromSource(
                uniqueCode(prefix),
                period.getId(),
                prefix,
                "RECEIVABLE_ALLOWANCE",
                UUID.randomUUID(),
                PERIOD
        );
        setField(journal, "status", JournalStatus.POSTED);
        setField(journal, "postedAt", postedAt);
        journalEntryRepository.saveAndFlush(journal);
    }

    private long countFreshAllowance(AccountingPeriod period, Instant generatedAt) {
        return journalEntryRepository
                .countByAccountingPeriodIdAndSourceModuleAndSourceDocumentNumberAndStatusAndPostedAtGreaterThanEqual(
                        period.getId(),
                        "RECEIVABLE_ALLOWANCE",
                        PERIOD,
                        JournalStatus.POSTED,
                        generatedAt
                );
    }

    private static String uniqueCode(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot set test field " + fieldName, exception);
        }
    }
}
