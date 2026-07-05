package id.pdam.sia.payment.application;

import id.pdam.sia.accounting.application.AccountingApplicationService;
import id.pdam.sia.accounting.application.PaymentSettlementPostingCommand;
import id.pdam.sia.accounting.domain.JournalEntry;
import id.pdam.sia.billing.domain.Invoice;
import id.pdam.sia.billing.repository.InvoiceRepository;
import id.pdam.sia.payment.domain.Payment;
import id.pdam.sia.payment.domain.PaymentAllocation;
import id.pdam.sia.payment.domain.PaymentReceipt;
import id.pdam.sia.payment.repository.PaymentAllocationRepository;
import id.pdam.sia.payment.repository.PaymentReceiptRepository;
import id.pdam.sia.payment.repository.PaymentRepository;
import id.pdam.sia.payment.web.PaymentAllocationRequest;
import id.pdam.sia.payment.web.SettleCounterPaymentRequest;
import id.pdam.sia.shared.audit.AuditTrailService;
import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.idempotency.IdempotencyRecord;
import id.pdam.sia.shared.idempotency.IdempotencyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PaymentSettlementApplicationService {
    private static final String CHANNEL_COUNTER = "COUNTER";
    private static final String IDEMPOTENCY_MODULE = "PAYMENT_SETTLEMENT";
    private static final DateTimeFormatter NUMBER_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE.withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM").withZone(ZoneOffset.UTC);

    private final PaymentRepository paymentRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final PaymentReceiptRepository paymentReceiptRepository;
    private final InvoiceRepository invoiceRepository;
    private final IdempotencyService idempotencyService;
    private final AuditTrailService auditTrailService;
    private final AccountingApplicationService accountingApplicationService;

    public PaymentSettlementApplicationService(
            PaymentRepository paymentRepository,
            PaymentAllocationRepository paymentAllocationRepository,
            PaymentReceiptRepository paymentReceiptRepository,
            InvoiceRepository invoiceRepository,
            IdempotencyService idempotencyService,
            AuditTrailService auditTrailService,
            AccountingApplicationService accountingApplicationService
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.paymentReceiptRepository = paymentReceiptRepository;
        this.invoiceRepository = invoiceRepository;
        this.idempotencyService = idempotencyService;
        this.auditTrailService = auditTrailService;
        this.accountingApplicationService = accountingApplicationService;
    }

    @Transactional
    public PaymentSettlementResult settleCounterPayment(
            SettleCounterPaymentRequest request,
            String idempotencyKey,
            String actor
    ) {
        if (request == null) {
            throw new BusinessException("PAYMENT_REQUEST_REQUIRED", "Payment request is required.");
        }
        String normalizedIdempotencyKey = requireNormalize(
                idempotencyKey,
                "PAYMENT_IDEMPOTENCY_KEY_REQUIRED",
                "Payment idempotency key is required."
        );
        String externalReference = normalizeOptional(request.externalReference());
        BigDecimal amount = requirePositive(request.amount(), "PAYMENT_AMOUNT_REQUIRED", "Payment amount is required.");
        Instant paidAt = requirePaidAt(request.paidAt());
        String reason = requireNormalize(request.reason(), "PAYMENT_REASON_REQUIRED", "Payment reason is required.");
        UUID cashAccountId = requireUuid(request.cashAccountId(), "PAYMENT_CASH_ACCOUNT_REQUIRED", "Payment cash or bank account is required.");
        UUID receivableAccountId = requireUuid(request.receivableAccountId(), "PAYMENT_RECEIVABLE_ACCOUNT_REQUIRED", "Payment receivable account is required.");
        if (cashAccountId.equals(receivableAccountId)) {
            throw new BusinessException("PAYMENT_POSTING_ACCOUNT_DUPLICATE", "Cash/bank account and receivable account must be different.");
        }
        List<AllocationCommand> allocations = normalizeAllocations(request.allocations());
        ensureAllocationTotalMatchesPayment(amount, allocations);

        IdempotencyRecord idempotencyRecord = idempotencyService.reserve(
                normalizedIdempotencyKey,
                IDEMPOTENCY_MODULE,
                requestHash(externalReference, amount, paidAt, cashAccountId, receivableAccountId, allocations),
                Instant.now().plus(Duration.ofHours(24))
        );
        if (idempotencyRecord.isCompleted()) {
            return existingResult(idempotencyRecord);
        }

        if (externalReference != null) {
            paymentRepository.findByExternalReference(externalReference).ifPresent(existing -> {
                throw new BusinessException("PAYMENT_EXTERNAL_REFERENCE_DUPLICATE", "Payment external reference already exists.");
            });
        }

        Map<UUID, Invoice> invoices = loadInvoices(allocations);
        Payment payment = paymentRepository.save(new Payment(
                paymentNumber(normalizedIdempotencyKey, paidAt),
                normalizedIdempotencyKey,
                CHANNEL_COUNTER,
                externalReference,
                amount,
                paidAt
        ));

        List<PaymentAllocation> savedAllocations = allocations.stream()
                .map(allocation -> persistAllocation(payment, invoices.get(allocation.invoiceId()), allocation.amount()))
                .toList();
        PaymentReceipt receipt = paymentReceiptRepository.save(new PaymentReceipt(
                payment.getId(),
                receiptNumber(normalizedIdempotencyKey, paidAt),
                paidAt
        ));
        JournalEntry settlementJournal = accountingApplicationService.postPaymentSettlement(
                new PaymentSettlementPostingCommand(
                        payment.getPaymentNumber(),
                        payment.getId(),
                        paymentPeriod(paidAt),
                        amount,
                        cashAccountId,
                        receivableAccountId,
                        reason
                ),
                actor
        );
        payment.linkSettlementJournal(settlementJournal.getId());
        Payment savedPayment = paymentRepository.save(payment);

        idempotencyRecord.markCompleted(savedPayment.getId().toString());
        auditTrailService.record(actor, "PAYMENT", "SETTLE_COUNTER_PAYMENT", savedPayment.getId().toString(), reason);

        return new PaymentSettlementResult(savedPayment, receipt, savedAllocations);
    }

    private PaymentSettlementResult existingResult(IdempotencyRecord idempotencyRecord) {
        String responseReference = idempotencyRecord.getResponseReference();
        if (responseReference == null || responseReference.isBlank()) {
            throw new BusinessException("PAYMENT_IDEMPOTENCY_RESPONSE_MISSING", "Completed payment idempotency record has no response reference.");
        }
        UUID paymentId = UUID.fromString(responseReference);
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "Payment was not found."));
        PaymentReceipt receipt = paymentReceiptRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new BusinessException("PAYMENT_RECEIPT_NOT_FOUND", "Payment receipt was not found."));
        List<PaymentAllocation> allocations = paymentAllocationRepository.findByPaymentId(paymentId);
        return new PaymentSettlementResult(payment, receipt, allocations);
    }

    private Map<UUID, Invoice> loadInvoices(List<AllocationCommand> allocations) {
        List<UUID> invoiceIds = allocations.stream()
                .map(AllocationCommand::invoiceId)
                .toList();
        Map<UUID, Invoice> invoices = invoiceRepository.findAllById(invoiceIds).stream()
                .collect(Collectors.toMap(Invoice::getId, Function.identity()));
        for (UUID invoiceId : invoiceIds) {
            if (!invoices.containsKey(invoiceId)) {
                throw new BusinessException("PAYMENT_INVOICE_NOT_FOUND", "Payment invoice was not found.");
            }
        }
        return invoices;
    }

    private PaymentAllocation persistAllocation(Payment payment, Invoice invoice, BigDecimal amount) {
        invoice.applyPayment(amount);
        return paymentAllocationRepository.save(new PaymentAllocation(payment.getId(), invoice.getId(), amount));
    }

    private static List<AllocationCommand> normalizeAllocations(List<PaymentAllocationRequest> allocations) {
        if (allocations == null || allocations.isEmpty()) {
            throw new BusinessException("PAYMENT_ALLOCATIONS_REQUIRED", "Payment allocations are required.");
        }
        Map<UUID, AllocationCommand> commands = new LinkedHashMap<>();
        for (PaymentAllocationRequest allocation : allocations) {
            if (allocation == null || allocation.invoiceId() == null) {
                throw new BusinessException("PAYMENT_ALLOCATION_INVOICE_REQUIRED", "Payment allocation invoice is required.");
            }
            if (commands.containsKey(allocation.invoiceId())) {
                throw new BusinessException("PAYMENT_ALLOCATION_INVOICE_DUPLICATE", "Payment allocation invoice is duplicated.");
            }
            commands.put(allocation.invoiceId(), new AllocationCommand(
                    allocation.invoiceId(),
                    requirePositive(allocation.amount(), "PAYMENT_ALLOCATION_AMOUNT_REQUIRED", "Payment allocation amount is required.")
            ));
        }
        return List.copyOf(commands.values());
    }

    private static void ensureAllocationTotalMatchesPayment(BigDecimal amount, List<AllocationCommand> allocations) {
        BigDecimal total = allocations.stream()
                .map(AllocationCommand::amount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        if (total.compareTo(amount) != 0) {
            throw new BusinessException("PAYMENT_ALLOCATION_TOTAL_MISMATCH", "Payment allocation total must equal payment amount.");
        }
    }

    private static Instant requirePaidAt(Instant paidAt) {
        if (paidAt == null) {
            throw new BusinessException("PAYMENT_PAID_AT_REQUIRED", "Payment paid timestamp is required.");
        }
        return paidAt;
    }

    private static UUID requireUuid(UUID value, String code, String message) {
        if (value == null) {
            throw new BusinessException(code, message);
        }
        return value;
    }

    private static BigDecimal requirePositive(BigDecimal value, String code, String message) {
        if (value == null) {
            throw new BusinessException(code, message);
        }
        BigDecimal normalized = value.setScale(2, RoundingMode.HALF_UP);
        if (normalized.signum() <= 0) {
            throw new BusinessException("PAYMENT_AMOUNT_INVALID", "Payment amount must be greater than zero.");
        }
        return normalized;
    }

    private static String requireNormalize(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String paymentNumber(String idempotencyKey, Instant paidAt) {
        return "PAY-" + NUMBER_DATE_FORMATTER.format(paidAt) + "-" + shortHash(idempotencyKey);
    }

    private static String receiptNumber(String idempotencyKey, Instant paidAt) {
        return "RCT-" + NUMBER_DATE_FORMATTER.format(paidAt) + "-" + shortHash(idempotencyKey);
    }

    private static String paymentPeriod(Instant paidAt) {
        return PERIOD_FORMATTER.format(paidAt);
    }

    private static String requestHash(
            String externalReference,
            BigDecimal amount,
            Instant paidAt,
            UUID cashAccountId,
            UUID receivableAccountId,
            List<AllocationCommand> allocations
    ) {
        String allocationPayload = allocations.stream()
                .sorted(Comparator.comparing(allocation -> allocation.invoiceId().toString()))
                .map(allocation -> allocation.invoiceId() + ":" + allocation.amount().toPlainString())
                .collect(Collectors.joining("|"));
        return sha256(CHANNEL_COUNTER + "\n"
                + (externalReference == null ? "" : externalReference) + "\n"
                + amount.toPlainString() + "\n"
                + paidAt + "\n"
                + cashAccountId + "\n"
                + receivableAccountId + "\n"
                + allocationPayload);
    }

    private static String shortHash(String value) {
        return sha256(value).substring("sha256:".length(), "sha256:".length() + 12).toUpperCase();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record AllocationCommand(UUID invoiceId, BigDecimal amount) {
    }
}
