package id.pdam.sia.receivable.application;

import id.pdam.sia.accounting.application.AccountingBlueprintApplicationService;
import id.pdam.sia.accounting.domain.JournalEntry;
import id.pdam.sia.billing.domain.Invoice;
import id.pdam.sia.billing.domain.InvoiceStatus;
import id.pdam.sia.billing.repository.InvoiceRepository;
import id.pdam.sia.connection.domain.Connection;
import id.pdam.sia.connection.repository.ConnectionRepository;
import id.pdam.sia.receivable.domain.CollectionAction;
import id.pdam.sia.receivable.domain.CollectionActionStatus;
import id.pdam.sia.receivable.domain.CollectionActionType;
import id.pdam.sia.receivable.domain.InstallmentItem;
import id.pdam.sia.receivable.domain.InstallmentPlan;
import id.pdam.sia.receivable.domain.InstallmentPlanStatus;
import id.pdam.sia.receivable.repository.CollectionActionRepository;
import id.pdam.sia.receivable.repository.InstallmentItemRepository;
import id.pdam.sia.receivable.repository.InstallmentPlanRepository;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ReceivableBlueprintApplicationService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final DateTimeFormatter PLAN_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final InvoiceRepository invoiceRepository;
    private final ConnectionRepository connectionRepository;
    private final CollectionActionRepository collectionActionRepository;
    private final InstallmentPlanRepository installmentPlanRepository;
    private final InstallmentItemRepository installmentItemRepository;
    private final AccountingBlueprintApplicationService accountingBlueprintApplicationService;
    private final AuditTrailService auditTrailService;

    public ReceivableBlueprintApplicationService(
            InvoiceRepository invoiceRepository,
            ConnectionRepository connectionRepository,
            CollectionActionRepository collectionActionRepository,
            InstallmentPlanRepository installmentPlanRepository,
            InstallmentItemRepository installmentItemRepository,
            AccountingBlueprintApplicationService accountingBlueprintApplicationService,
            AuditTrailService auditTrailService
    ) {
        this.invoiceRepository = invoiceRepository;
        this.connectionRepository = connectionRepository;
        this.collectionActionRepository = collectionActionRepository;
        this.installmentPlanRepository = installmentPlanRepository;
        this.installmentItemRepository = installmentItemRepository;
        this.accountingBlueprintApplicationService = accountingBlueprintApplicationService;
        this.auditTrailService = auditTrailService;
    }

    @Transactional(readOnly = true)
    public Page<InstallmentPlan> listPlans(InstallmentPlanStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        if (status == null) {
            return installmentPlanRepository.findAll(pageable);
        }
        return installmentPlanRepository.findByStatus(status, pageable);
    }

    @Transactional(readOnly = true)
    public InstallmentPlanResult getPlan(UUID planId) {
        InstallmentPlan plan = installmentPlanRepository.findById(requireUuid(planId, "INSTALLMENT_PLAN_ID_REQUIRED", "Installment plan id is required."))
                .orElseThrow(() -> new BusinessException("INSTALLMENT_PLAN_NOT_FOUND", "Installment plan was not found."));
        return new InstallmentPlanResult(plan, installmentItemRepository.findByPlanIdOrderByInstallmentNumberAsc(plan.getId()));
    }

    @Transactional
    public InstallmentPlanResult createPlan(CreateInstallmentPlanCommand command, String actor) {
        Invoice invoice = invoiceRepository.findById(requireUuid(command.invoiceId(), "INSTALLMENT_INVOICE_REQUIRED", "Installment invoice is required."))
                .orElseThrow(() -> new BusinessException("INSTALLMENT_INVOICE_NOT_FOUND", "Installment invoice was not found."));
        if (invoice.getStatus() != InvoiceStatus.ISSUED && invoice.getStatus() != InvoiceStatus.PARTIAL_PAID) {
            throw new BusinessException("INSTALLMENT_INVOICE_STATUS_INVALID", "Installment invoice must be an open receivable.");
        }
        if (invoice.getOutstandingAmount().signum() <= 0) {
            throw new BusinessException("INSTALLMENT_INVOICE_PAID", "Installment invoice must have outstanding balance.");
        }
        if (installmentPlanRepository.existsByInvoiceIdAndStatus(invoice.getId(), InstallmentPlanStatus.ACTIVE)) {
            throw new BusinessException("INSTALLMENT_PLAN_ACTIVE_DUPLICATE", "Invoice already has an active installment plan.");
        }
        if (command.installmentCount() < 1 || command.installmentCount() > 36) {
            throw new BusinessException("INSTALLMENT_COUNT_INVALID", "Installment count must be between 1 and 36.");
        }
        if (command.firstDueDate() == null) {
            throw new BusinessException("INSTALLMENT_FIRST_DUE_DATE_REQUIRED", "Installment first due date is required.");
        }

        String planNumber = planNumber(invoice.getInvoiceNumber());
        InstallmentPlan plan = installmentPlanRepository.save(new InstallmentPlan(
                invoice.getId(),
                planNumber,
                invoice.getOutstandingAmount(),
                command.installmentCount(),
                actor,
                command.notes()
        ));
        List<InstallmentItem> items = installmentItemRepository.saveAll(buildItems(
                plan.getId(),
                invoice.getOutstandingAmount(),
                command.installmentCount(),
                command.firstDueDate()
        ));
        auditTrailService.record(actor, "RECEIVABLE", "CREATE_INSTALLMENT_PLAN", plan.getId().toString(), command.reason());
        return new InstallmentPlanResult(plan, items);
    }

    @Transactional
    public DunningRunResult runDunning(RunDunningCommand command, String actor) {
        LocalDate asOfDate = command.asOfDate() == null ? LocalDate.now(ZoneOffset.UTC) : command.asOfDate();
        Instant scheduledAt = asOfDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        List<Invoice> openInvoices = invoiceRepository.findOpenReceivables();
        int created = 0;
        int skipped = 0;
        for (Invoice invoice : openInvoices) {
            if (invoice.getDueDate() == null || !invoice.getDueDate().isBefore(asOfDate)) {
                skipped++;
                continue;
            }
            if (collectionActionRepository.existsByInvoiceIdAndActionTypeAndStatusIn(
                    invoice.getId(),
                    CollectionActionType.WARNING_LETTER,
                    CollectionActionStatus.activeStatuses()
            )) {
                skipped++;
                continue;
            }
            Connection connection = connectionRepository.findById(invoice.getConnectionId())
                    .orElseThrow(() -> new BusinessException("DUNNING_CONNECTION_NOT_FOUND", "Dunning invoice connection was not found."));
            collectionActionRepository.save(new CollectionAction(
                    connection.getCustomerId(),
                    invoice.getId(),
                    CollectionActionType.WARNING_LETTER,
                    scheduledAt,
                    "Auto dunning " + asOfDate + " for invoice " + invoice.getInvoiceNumber()
            ));
            created++;
        }
        auditTrailService.record(actor, "RECEIVABLE", "RUN_DUNNING", asOfDate.toString(), "created=" + created + ";skipped=" + skipped);
        return new DunningRunResult(asOfDate, openInvoices.size(), created, skipped);
    }

    @Transactional
    public JournalEntry postAllowance(PostAllowanceCommand command, String actor) {
        JournalEntry journal = accountingBlueprintApplicationService.postReceivableAllowance(
                new AccountingBlueprintApplicationService.PostReceivableAllowanceCommand(
                        command.period(),
                        command.amount(),
                        command.expenseAccountId(),
                        command.allowanceAccountId(),
                        command.reason()
                ),
                actor
        );
        auditTrailService.record(actor, "RECEIVABLE", "POST_ALLOWANCE", journal.getId().toString(), command.reason());
        return journal;
    }

    private static List<InstallmentItem> buildItems(UUID planId, BigDecimal total, int count, LocalDate firstDueDate) {
        BigDecimal base = total.divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
        BigDecimal allocated = BigDecimal.ZERO.setScale(2);
        List<InstallmentItem> items = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            BigDecimal amount = index == count ? total.subtract(allocated).setScale(2, RoundingMode.HALF_UP) : base;
            allocated = allocated.add(amount).setScale(2, RoundingMode.HALF_UP);
            items.add(new InstallmentItem(planId, index, firstDueDate.plusMonths(index - 1L), amount));
        }
        return items;
    }

    private static String planNumber(String invoiceNumber) {
        return "INS-" + PLAN_DATE_FORMATTER.format(LocalDate.now(ZoneOffset.UTC)) + "-" + invoiceNumber;
    }

    private static UUID requireUuid(UUID value, String code, String message) {
        if (value == null) {
            throw new BusinessException(code, message);
        }
        return value;
    }

    public record CreateInstallmentPlanCommand(UUID invoiceId, int installmentCount, LocalDate firstDueDate, String notes, String reason) {
    }

    public record RunDunningCommand(LocalDate asOfDate) {
    }

    public record PostAllowanceCommand(String period, BigDecimal amount, UUID expenseAccountId, UUID allowanceAccountId, String reason) {
    }

    public record InstallmentPlanResult(InstallmentPlan plan, List<InstallmentItem> items) {
        public InstallmentPlanResult {
            items = List.copyOf(items);
        }
    }

    public record DunningRunResult(LocalDate asOfDate, int candidateInvoices, int createdActions, int skippedInvoices) {
    }
}
