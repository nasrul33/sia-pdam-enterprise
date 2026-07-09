package id.pdam.sia.billing.application;

import id.pdam.sia.accounting.application.AccountingApplicationService;
import id.pdam.sia.accounting.application.BillingInvoicePostingCommand;
import id.pdam.sia.accounting.application.BillingInvoiceVoidPostingCommand;
import id.pdam.sia.accounting.domain.JournalEntry;
import id.pdam.sia.billing.domain.BillingBatch;
import id.pdam.sia.billing.domain.BillingBatchStatus;
import id.pdam.sia.billing.domain.Invoice;
import id.pdam.sia.billing.domain.InvoiceLine;
import id.pdam.sia.billing.domain.InvoiceLineType;
import id.pdam.sia.billing.domain.InvoiceStatus;
import id.pdam.sia.billing.repository.BillingBatchRepository;
import id.pdam.sia.billing.repository.InvoiceLineRepository;
import id.pdam.sia.billing.repository.InvoiceRepository;
import id.pdam.sia.billing.web.GenerateBillingBatchRequest;
import id.pdam.sia.billing.web.IssueInvoiceRequest;
import id.pdam.sia.billing.web.VoidInvoiceRequest;
import id.pdam.sia.billing.web.CalculateTariffRequest;
import id.pdam.sia.connection.domain.Connection;
import id.pdam.sia.connection.domain.ConnectionStatus;
import id.pdam.sia.connection.repository.ConnectionRepository;
import id.pdam.sia.metering.domain.MeterReading;
import id.pdam.sia.metering.repository.MeterReadingRepository;
import id.pdam.sia.shared.audit.AuditTrailService;
import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.idempotency.IdempotencyRecord;
import id.pdam.sia.shared.idempotency.IdempotencyService;
import id.pdam.sia.shared.money.CurrencyCode;
import id.pdam.sia.shared.money.Money;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class BillingBatchApplicationService {
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM");
    private static final int MAX_PAGE_SIZE = 100;

    private final BillingBatchRepository billingBatchRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final MeterReadingRepository meterReadingRepository;
    private final ConnectionRepository connectionRepository;
    private final TariffEngineApplicationService tariffEngineApplicationService;
    private final IdempotencyService idempotencyService;
    private final AuditTrailService auditTrailService;
    private final AccountingApplicationService accountingApplicationService;

    public BillingBatchApplicationService(
            BillingBatchRepository billingBatchRepository,
            InvoiceRepository invoiceRepository,
            InvoiceLineRepository invoiceLineRepository,
            MeterReadingRepository meterReadingRepository,
            ConnectionRepository connectionRepository,
            TariffEngineApplicationService tariffEngineApplicationService,
            IdempotencyService idempotencyService,
            AuditTrailService auditTrailService,
            AccountingApplicationService accountingApplicationService
    ) {
        this.billingBatchRepository = billingBatchRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.meterReadingRepository = meterReadingRepository;
        this.connectionRepository = connectionRepository;
        this.tariffEngineApplicationService = tariffEngineApplicationService;
        this.idempotencyService = idempotencyService;
        this.auditTrailService = auditTrailService;
        this.accountingApplicationService = accountingApplicationService;
    }

    @Transactional(readOnly = true)
    public Page<BillingBatch> listBatches(String period, BillingBatchStatus status, int page, int size) {
        Pageable pageable = pageable(page, size, Sort.by("createdAt").descending());
        String normalizedPeriod = normalizeOptionalPeriod(period);
        if (normalizedPeriod != null && status != null) {
            return billingBatchRepository.findByPeriodAndStatus(normalizedPeriod, status, pageable);
        }
        if (normalizedPeriod != null) {
            return billingBatchRepository.findByPeriod(normalizedPeriod, pageable);
        }
        if (status != null) {
            return billingBatchRepository.findByStatus(status, pageable);
        }
        return billingBatchRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public BillingBatch getBatch(UUID batchId) {
        return billingBatchRepository.findById(batchId)
                .orElseThrow(() -> new BusinessException("BILLING_BATCH_NOT_FOUND", "Billing batch was not found."));
    }

    @Transactional(readOnly = true)
    public List<Invoice> listBatchInvoices(UUID batchId) {
        getBatch(batchId);
        return invoiceRepository.findByBillingBatchId(batchId);
    }

    @Transactional(readOnly = true)
    public BillingBatchIssueReadiness issueReadiness(UUID batchId) {
        BillingBatch batch = getBatch(batchId);
        List<Invoice> invoices = invoiceRepository.findByBillingBatchId(batch.getId());
        long draftInvoices = invoices.stream()
                .filter(invoice -> invoice.getStatus() == InvoiceStatus.DRAFT)
                .count();
        long issuedOrPaidInvoices = invoices.stream()
                .filter(BillingBatchApplicationService::hasFinancialImpact)
                .count();
        long blockedInvoices = invoices.size() - draftInvoices;
        long missingJournalTraceInvoices = invoices.stream()
                .filter(BillingBatchApplicationService::hasFinancialImpact)
                .filter(invoice -> invoice.getIssueJournalEntryId() == null)
                .count();
        BigDecimal draftAmount = invoices.stream()
                .filter(invoice -> invoice.getStatus() == InvoiceStatus.DRAFT)
                .map(Invoice::getOutstandingAmount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        BigDecimal outstandingAmount = invoices.stream()
                .map(Invoice::getOutstandingAmount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        boolean readyToIssue = batch.getStatus() == BillingBatchStatus.COMPLETED
                && draftInvoices > 0
                && missingJournalTraceInvoices == 0;

        return new BillingBatchIssueReadiness(
                batch,
                invoices.size(),
                draftInvoices,
                issuedOrPaidInvoices,
                blockedInvoices,
                missingJournalTraceInvoices,
                draftAmount,
                outstandingAmount,
                readyToIssue
        );
    }

    @Transactional(readOnly = true)
    public Page<Invoice> listInvoices(String period, InvoiceStatus status, int page, int size) {
        Pageable pageable = pageable(page, size, Sort.by("createdAt").descending());
        String normalizedPeriod = normalizeOptionalPeriod(period);
        if (normalizedPeriod != null && status != null) {
            return invoiceRepository.findByPeriodAndStatus(normalizedPeriod, status, pageable);
        }
        if (normalizedPeriod != null) {
            return invoiceRepository.findByPeriod(normalizedPeriod, pageable);
        }
        if (status != null) {
            return invoiceRepository.findByStatus(status, pageable);
        }
        return invoiceRepository.findAll(pageable);
    }

    @Transactional
    public Invoice issueInvoice(UUID invoiceId, IssueInvoiceRequest request, String actor) {
        if (invoiceId == null) {
            throw new BusinessException("INVOICE_ID_REQUIRED", "Invoice id is required.");
        }
        if (request == null) {
            throw new BusinessException("INVOICE_ISSUE_REQUEST_REQUIRED", "Invoice issue request is required.");
        }

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new BusinessException("INVOICE_NOT_FOUND", "Invoice was not found."));
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new BusinessException("INVOICE_ISSUE_STATUS_INVALID", "Only draft invoice can be issued.");
        }
        JournalEntry journal = accountingApplicationService.postBillingInvoice(
                new BillingInvoicePostingCommand(
                        invoice.getInvoiceNumber(),
                        invoice.getId(),
                        invoice.getPeriod(),
                        invoice.getOutstandingAmount(),
                        request.receivableAccountId(),
                        request.revenueAccountId(),
                        request.reason()
                ),
                actor
        );
        invoice.markIssued(Instant.now(), journal.getId());
        Invoice saved = invoiceRepository.save(invoice);
        auditTrailService.record(actor, "BILLING", "ISSUE_INVOICE", saved.getId().toString(), request.reason());
        return saved;
    }

    @Transactional
    public Invoice voidInvoice(UUID invoiceId, VoidInvoiceRequest request, String actor) {
        if (invoiceId == null) {
            throw new BusinessException("INVOICE_ID_REQUIRED", "Invoice id is required.");
        }
        if (request == null) {
            throw new BusinessException("INVOICE_VOID_REQUEST_REQUIRED", "Invoice void request is required.");
        }

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new BusinessException("INVOICE_NOT_FOUND", "Invoice was not found."));
        if (invoice.getStatus() != InvoiceStatus.ISSUED) {
            throw new BusinessException("INVOICE_VOID_STATUS_INVALID", "Only issued unpaid invoice can be voided.");
        }
        if (invoice.getIssueJournalEntryId() == null) {
            throw new BusinessException("INVOICE_VOID_ISSUE_JOURNAL_REQUIRED", "Issued invoice must have journal trace before void.");
        }
        if (invoice.getPaidAmount().signum() > 0) {
            throw new BusinessException("INVOICE_VOID_PAID_INVALID", "Paid or partial paid invoice must be reversed before void.");
        }

        JournalEntry journal = accountingApplicationService.postBillingInvoiceVoid(
                new BillingInvoiceVoidPostingCommand(
                        invoice.getInvoiceNumber(),
                        invoice.getId(),
                        invoice.getPeriod(),
                        invoice.getIssueJournalEntryId(),
                        request.reason()
                ),
                actor
        );
        invoice.voidUnpaid(Instant.now(), journal.getId());
        Invoice saved = invoiceRepository.save(invoice);
        auditTrailService.record(actor, "BILLING", "VOID_INVOICE", saved.getId().toString(), request.reason());
        return saved;
    }

    @Transactional
    public BillingBatchGenerationResult generateBatch(
            GenerateBillingBatchRequest request,
            String idempotencyKey,
            String actor
    ) {
        String period = normalizePeriod(request.period());
        String areaCode = requireNormalize(request.areaCode(), "BILLING_AREA_REQUIRED", "Billing area code is required.");
        validateDates(request.billingDate(), request.dueDate());
        String normalizedIdempotencyKey = requireNormalize(
                idempotencyKey,
                "BILLING_IDEMPOTENCY_KEY_REQUIRED",
                "Billing idempotency key is required."
        );
        String requestHash = requestHash(period, areaCode, request.billingDate(), request.dueDate());
        IdempotencyRecord idempotencyRecord = idempotencyService.reserve(
                normalizedIdempotencyKey,
                "BILLING_BATCH",
                requestHash,
                Instant.now().plus(Duration.ofHours(24))
        );
        if (idempotencyRecord.isCompleted()) {
            return existingResult(idempotencyRecord);
        }

        billingBatchRepository.findByPeriodAndAreaCode(period, areaCode).ifPresent(existing -> {
            throw new BusinessException("BILLING_BATCH_PERIOD_AREA_DUPLICATE", "Billing batch already exists for this period and area.");
        });

        List<MeterReading> readings = meterReadingRepository.findVerifiedByAreaCodeAndPeriod(areaCode, period);
        if (readings.isEmpty()) {
            throw new BusinessException("BILLING_BATCH_NO_VERIFIED_READINGS", "No verified meter readings were found for this period and area.");
        }

        List<InvoiceDraft> drafts = readings.stream()
                .map(reading -> prepareInvoiceDraft(reading, period, request.billingDate(), request.dueDate()))
                .toList();
        ensureUniqueInvoiceNumbers(drafts);

        BillingBatch batch = billingBatchRepository.save(new BillingBatch(
                batchNumber(period, areaCode),
                period,
                areaCode,
                normalizedIdempotencyKey
        ));

        List<Invoice> invoices = drafts.stream()
                .map(draft -> persistInvoice(batch, draft))
                .toList();
        batch.markCompleted();
        idempotencyRecord.markCompleted(batch.getId().toString());
        auditTrailService.record(actor, "BILLING", "GENERATE_BILLING_BATCH", batch.getId().toString(), request.reason());

        return new BillingBatchGenerationResult(batch, invoices, total(invoices));
    }

    private BillingBatchGenerationResult existingResult(IdempotencyRecord idempotencyRecord) {
        String responseReference = idempotencyRecord.getResponseReference();
        if (responseReference == null || responseReference.isBlank()) {
            throw new BusinessException("BILLING_IDEMPOTENCY_RESPONSE_MISSING", "Completed billing idempotency record has no response reference.");
        }
        UUID batchId = UUID.fromString(responseReference);
        BillingBatch batch = getBatch(batchId);
        List<Invoice> invoices = invoiceRepository.findByBillingBatchId(batch.getId());
        return new BillingBatchGenerationResult(batch, invoices, total(invoices));
    }

    private InvoiceDraft prepareInvoiceDraft(
            MeterReading reading,
            String period,
            LocalDate billingDate,
            LocalDate dueDate
    ) {
        Connection connection = connectionRepository.findById(reading.getConnectionId())
                .orElseThrow(() -> new BusinessException("CONNECTION_NOT_FOUND", "Connection was not found."));
        if (connection.getStatus() != ConnectionStatus.ACTIVE) {
            throw new BusinessException("CONNECTION_NOT_ACTIVE", "Billing can only be generated for active connection.");
        }
        if (invoiceRepository.existsByConnectionIdAndPeriod(connection.getId(), period)) {
            throw new BusinessException("INVOICE_CONNECTION_PERIOD_DUPLICATE", "Invoice already exists for this connection and period.");
        }

        TariffCalculationResult calculation = tariffEngineApplicationService.calculate(new CalculateTariffRequest(
                connection.getTariffGroupId(),
                billingDate,
                reading.getUsageM3()
        ));

        return new InvoiceDraft(
                connection.getId(),
                invoiceNumber(period, connection.getConnectionNumber()),
                dueDate,
                calculation
        );
    }

    private Invoice persistInvoice(BillingBatch batch, InvoiceDraft draft) {
        Invoice invoice = invoiceRepository.save(new Invoice(
                batch.getId(),
                draft.connectionId(),
                draft.invoiceNumber(),
                batch.getPeriod(),
                draft.calculation().subtotal().amount(),
                draft.dueDate()
        ));

        for (TariffCalculationLine line : draft.calculation().lines()) {
            invoiceLineRepository.save(new InvoiceLine(
                    invoice.getId(),
                    InvoiceLineType.WATER_USAGE,
                    "Pemakaian air blok " + line.blockOrder(),
                    line.quantityM3(),
                    line.pricePerM3(),
                    line.amount().amount()
            ));
        }
        return invoice;
    }

    private static void ensureUniqueInvoiceNumbers(List<InvoiceDraft> drafts) {
        Set<String> invoiceNumbers = new LinkedHashSet<>();
        for (InvoiceDraft draft : drafts) {
            if (!invoiceNumbers.add(draft.invoiceNumber())) {
                throw new BusinessException("INVOICE_NUMBER_DUPLICATE_IN_BATCH", "Invoice number is duplicated inside billing batch.");
            }
        }
    }

    private static Money total(List<Invoice> invoices) {
        return invoices.stream()
                .map(invoice -> Money.of(invoice.getSubtotal(), CurrencyCode.IDR))
                .reduce(Money.zero(), Money::add);
    }

    private static boolean hasFinancialImpact(Invoice invoice) {
        return invoice.getStatus() == InvoiceStatus.ISSUED
                || invoice.getStatus() == InvoiceStatus.PARTIAL_PAID
                || invoice.getStatus() == InvoiceStatus.PAID;
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
        return normalizePeriod(period);
    }

    private static String normalizePeriod(String period) {
        String normalized = requireNormalize(period, "BILLING_PERIOD_REQUIRED", "Billing period is required.");
        try {
            YearMonth.parse(normalized, PERIOD_FORMATTER);
        } catch (DateTimeException exception) {
            throw new BusinessException("BILLING_PERIOD_INVALID", "Billing period must use yyyy-MM format.");
        }
        return normalized;
    }

    private static void validateDates(LocalDate billingDate, LocalDate dueDate) {
        if (billingDate == null) {
            throw new BusinessException("BILLING_DATE_REQUIRED", "Billing date is required.");
        }
        if (dueDate == null) {
            throw new BusinessException("BILLING_DUE_DATE_REQUIRED", "Billing due date is required.");
        }
        if (dueDate.isBefore(billingDate)) {
            throw new BusinessException("BILLING_DUE_DATE_INVALID", "Billing due date cannot be before billing date.");
        }
    }

    private static String requireNormalize(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    private static String batchNumber(String period, String areaCode) {
        return "BB-" + period.replace("-", "") + "-" + areaCode;
    }

    private static String invoiceNumber(String period, String connectionNumber) {
        return "INV-" + period.replace("-", "") + "-" + connectionNumber;
    }

    private static String requestHash(String period, String areaCode, LocalDate billingDate, LocalDate dueDate) {
        String payload = period + "|" + areaCode + "|" + billingDate + "|" + dueDate;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record InvoiceDraft(
            UUID connectionId,
            String invoiceNumber,
            LocalDate dueDate,
            TariffCalculationResult calculation
    ) {
    }
}
