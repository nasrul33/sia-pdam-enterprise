package id.pdam.sia.receivable.application;

import id.pdam.sia.accounting.application.AccountingBlueprintApplicationService;
import id.pdam.sia.accounting.domain.JournalEntry;
import id.pdam.sia.billing.domain.Invoice;
import id.pdam.sia.billing.repository.InvoiceRepository;
import id.pdam.sia.connection.domain.Connection;
import id.pdam.sia.connection.repository.ConnectionRepository;
import id.pdam.sia.receivable.domain.CollectionAction;
import id.pdam.sia.receivable.domain.CollectionActionStatus;
import id.pdam.sia.receivable.domain.CollectionActionType;
import id.pdam.sia.receivable.domain.InstallmentItem;
import id.pdam.sia.receivable.domain.InstallmentPlan;
import id.pdam.sia.receivable.repository.CollectionActionRepository;
import id.pdam.sia.receivable.repository.InstallmentItemRepository;
import id.pdam.sia.receivable.repository.InstallmentPlanRepository;
import id.pdam.sia.shared.audit.AuditTrailService;
import id.pdam.sia.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReceivableBlueprintApplicationServiceTest {
    private final InvoiceRepository invoiceRepository = mock(InvoiceRepository.class);
    private final ConnectionRepository connectionRepository = mock(ConnectionRepository.class);
    private final CollectionActionRepository collectionActionRepository = mock(CollectionActionRepository.class);
    private final InstallmentPlanRepository installmentPlanRepository = mock(InstallmentPlanRepository.class);
    private final InstallmentItemRepository installmentItemRepository = mock(InstallmentItemRepository.class);
    private final AccountingBlueprintApplicationService accountingBlueprintApplicationService =
            mock(AccountingBlueprintApplicationService.class);
    private final AuditTrailService auditTrailService = mock(AuditTrailService.class);
    private final ReceivableBlueprintApplicationService service = new ReceivableBlueprintApplicationService(
            invoiceRepository,
            connectionRepository,
            collectionActionRepository,
            installmentPlanRepository,
            installmentItemRepository,
            accountingBlueprintApplicationService,
            auditTrailService
    );

    @Test
    void createPlanSplitsOutstandingAmountAcrossInstallments() {
        Invoice invoice = issuedInvoice("INV-202607-001", new BigDecimal("100000.00"), LocalDate.of(2026, 8, 15));
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(installmentPlanRepository.existsByInvoiceIdAndStatus(eq(invoice.getId()), any())).thenReturn(false);
        when(installmentPlanRepository.save(any(InstallmentPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(installmentItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ReceivableBlueprintApplicationService.InstallmentPlanResult result = service.createPlan(
                new ReceivableBlueprintApplicationService.CreateInstallmentPlanCommand(
                        invoice.getId(),
                        3,
                        LocalDate.of(2026, 9, 1),
                        "Permohonan cicilan pelanggan",
                        "customer approved installment"
                ),
                "receivable.officer"
        );

        assertThat(result.plan().getInvoiceId()).isEqualTo(invoice.getId());
        assertThat(result.plan().getTotalAmount()).isEqualByComparingTo("100000.00");
        assertThat(result.items()).extracting(InstallmentItem::getInstallmentNumber).containsExactly(1, 2, 3);
        assertThat(result.items()).extracting(InstallmentItem::getDueDate)
                .containsExactly(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1));
        assertThat(result.items()).extracting(InstallmentItem::getAmount)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("33333.33"), new BigDecimal("33333.33"), new BigDecimal("33333.34"));
        verify(auditTrailService).record(
                "receivable.officer",
                "RECEIVABLE",
                "CREATE_INSTALLMENT_PLAN",
                result.plan().getId().toString(),
                "customer approved installment"
        );
    }

    @Test
    void createPlanRejectsPaidOrDraftInvoice() {
        Invoice draftInvoice = new Invoice(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "INV-DRAFT",
                "2026-07",
                new BigDecimal("50000.00"),
                LocalDate.of(2026, 8, 15)
        );
        when(invoiceRepository.findById(draftInvoice.getId())).thenReturn(Optional.of(draftInvoice));

        assertThatThrownBy(() -> service.createPlan(
                new ReceivableBlueprintApplicationService.CreateInstallmentPlanCommand(
                        draftInvoice.getId(),
                        2,
                        LocalDate.of(2026, 9, 1),
                        null,
                        "invalid status"
                ),
                "receivable.officer"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("open receivable");

        verify(installmentPlanRepository, never()).save(any(InstallmentPlan.class));
    }

    @Test
    void runDunningCreatesWarningLetterOnlyForOverdueOpenInvoicesWithoutActiveDuplicate() {
        Invoice overdueInvoice = issuedInvoice("INV-OVERDUE", new BigDecimal("75000.00"), LocalDate.of(2026, 7, 1));
        Invoice futureInvoice = issuedInvoice("INV-FUTURE", new BigDecimal("50000.00"), LocalDate.of(2026, 8, 20));
        Connection connection = new Connection(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "SR-001",
                "MTR-001",
                LocalDate.of(2026, 1, 1)
        );

        when(invoiceRepository.findOpenReceivables()).thenReturn(List.of(overdueInvoice, futureInvoice));
        when(collectionActionRepository.existsByInvoiceIdAndActionTypeAndStatusIn(
                overdueInvoice.getId(),
                CollectionActionType.WARNING_LETTER,
                CollectionActionStatus.activeStatuses()
        )).thenReturn(false);
        when(connectionRepository.findById(overdueInvoice.getConnectionId())).thenReturn(Optional.of(connection));
        when(collectionActionRepository.save(any(CollectionAction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReceivableBlueprintApplicationService.DunningRunResult result = service.runDunning(
                new ReceivableBlueprintApplicationService.RunDunningCommand(LocalDate.of(2026, 7, 31)),
                "collection.officer"
        );

        ArgumentCaptor<CollectionAction> actionCaptor = ArgumentCaptor.forClass(CollectionAction.class);
        verify(collectionActionRepository).save(actionCaptor.capture());

        assertThat(result.candidateInvoices()).isEqualTo(2);
        assertThat(result.createdActions()).isEqualTo(1);
        assertThat(result.skippedInvoices()).isEqualTo(1);
        assertThat(actionCaptor.getValue().getCustomerId()).isEqualTo(connection.getCustomerId());
        assertThat(actionCaptor.getValue().getInvoiceId()).isEqualTo(overdueInvoice.getId());
        assertThat(actionCaptor.getValue().getActionType()).isEqualTo(CollectionActionType.WARNING_LETTER);
        verify(auditTrailService).record(
                "collection.officer",
                "RECEIVABLE",
                "RUN_DUNNING",
                "2026-07-31",
                "created=1;skipped=1"
        );
    }

    @Test
    void postAllowanceDelegatesToAccountingBlueprintAndAuditsReceivableAction() {
        UUID expenseAccountId = UUID.randomUUID();
        UUID allowanceAccountId = UUID.randomUUID();
        JournalEntry journal = JournalEntry.draftFromSource(
                "REC-ALLOW-2026-07",
                UUID.randomUUID(),
                "Receivable allowance",
                "RECEIVABLE_ALLOWANCE",
                UUID.randomUUID(),
                "2026-07"
        );
        when(accountingBlueprintApplicationService.postReceivableAllowance(any(), eq("finance.manager")))
                .thenReturn(journal);

        JournalEntry result = service.postAllowance(
                new ReceivableBlueprintApplicationService.PostAllowanceCommand(
                        "2026-07",
                        new BigDecimal("350000.00"),
                        expenseAccountId,
                        allowanceAccountId,
                        "monthly allowance"
                ),
                "finance.manager"
        );

        ArgumentCaptor<AccountingBlueprintApplicationService.PostReceivableAllowanceCommand> commandCaptor =
                ArgumentCaptor.forClass(AccountingBlueprintApplicationService.PostReceivableAllowanceCommand.class);
        verify(accountingBlueprintApplicationService).postReceivableAllowance(commandCaptor.capture(), eq("finance.manager"));
        assertThat(commandCaptor.getValue().period()).isEqualTo("2026-07");
        assertThat(commandCaptor.getValue().amount()).isEqualByComparingTo("350000.00");
        assertThat(commandCaptor.getValue().expenseAccountId()).isEqualTo(expenseAccountId);
        assertThat(commandCaptor.getValue().allowanceAccountId()).isEqualTo(allowanceAccountId);
        assertThat(result).isSameAs(journal);
        verify(auditTrailService).record(
                "finance.manager",
                "RECEIVABLE",
                "POST_ALLOWANCE",
                journal.getId().toString(),
                "monthly allowance"
        );
    }

    private static Invoice issuedInvoice(String invoiceNumber, BigDecimal amount, LocalDate dueDate) {
        Invoice invoice = new Invoice(
                UUID.randomUUID(),
                UUID.randomUUID(),
                invoiceNumber,
                "2026-07",
                amount,
                dueDate
        );
        invoice.markIssued(Instant.parse("2026-07-05T00:00:00Z"), UUID.randomUUID());
        return invoice;
    }
}
