package id.pdam.sia.payment.application;

import id.pdam.sia.payment.domain.Payment;
import id.pdam.sia.payment.domain.PaymentStatus;
import id.pdam.sia.payment.repository.PaymentRepository;
import id.pdam.sia.shared.audit.AuditTrailService;
import id.pdam.sia.shared.exception.BusinessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Service
public class PaymentReconciliationApplicationService {
    private static final int MAX_EXPORT_ROWS = 10_000;
    private static final int MAX_MATCH_ROWS = 200;
    private static final List<PaymentStatus> RECONCILIABLE_STATUSES = List.of(PaymentStatus.SETTLED, PaymentStatus.REVERSED);
    private static final Sort PAYMENT_SORT = Sort.by(Sort.Direction.DESC, "paidAt")
            .and(Sort.by(Sort.Direction.DESC, "createdAt"));

    private final PaymentRepository paymentRepository;
    private final AuditTrailService auditTrailService;

    public PaymentReconciliationApplicationService(
            PaymentRepository paymentRepository,
            AuditTrailService auditTrailService
    ) {
        this.paymentRepository = paymentRepository;
        this.auditTrailService = auditTrailService;
    }

    @Transactional
    public List<PaymentReconciliationExportRow> exportPayments(PaymentReconciliationFilters filters, String actor) {
        PaymentReconciliationFilters normalized = normalizeFilters(filters);
        List<PaymentReconciliationExportRow> rows = paymentRepository
                .findAll(specification(normalized), PageRequest.of(0, MAX_EXPORT_ROWS, PAYMENT_SORT))
                .map(PaymentReconciliationExportRow::from)
                .getContent();

        auditTrailService.record(actor, "PAYMENT", "EXPORT_RECONCILIATION", "payment-reconciliation", auditReason(normalized, rows.size()));
        return rows;
    }

    @Transactional
    public PaymentReconciliationMatchReport matchBankStatementRows(List<BankStatementRowCommand> rows, String actor) {
        if (rows == null || rows.isEmpty()) {
            throw new BusinessException("PAYMENT_RECONCILIATION_ROWS_REQUIRED", "Bank statement rows are required.");
        }
        if (rows.size() > MAX_MATCH_ROWS) {
            throw new BusinessException("PAYMENT_RECONCILIATION_ROWS_LIMIT", "Bank statement rows cannot exceed 200 rows.");
        }

        List<PaymentReconciliationMatchResult> matches = java.util.stream.IntStream.range(0, rows.size())
                .mapToObj(index -> matchRow(index + 1, normalizeRow(rows.get(index))))
                .toList();

        auditTrailService.record(actor, "PAYMENT", "MATCH_BANK_STATEMENT", "payment-reconciliation", "rows=" + rows.size());
        return PaymentReconciliationMatchReport.from(matches);
    }

    private PaymentReconciliationMatchResult matchRow(int rowNumber, BankStatementRowCommand row) {
        Optional<Payment> referenceMatch = findReferenceMatch(row.statementReference());
        if (referenceMatch.isPresent()) {
            return resultForPayment(rowNumber, row, referenceMatch.get(), true);
        }

        List<Payment> candidates = paymentRepository.findAll(candidateSpecification(row), PAYMENT_SORT);
        if (candidates.isEmpty()) {
            return PaymentReconciliationMatchResult.unmatched(rowNumber, row);
        }
        if (candidates.size() > 1) {
            return PaymentReconciliationMatchResult.multiple(rowNumber, row, candidates.size());
        }
        return resultForPayment(rowNumber, row, candidates.get(0), false);
    }

    private PaymentReconciliationMatchResult resultForPayment(
            int rowNumber,
            BankStatementRowCommand row,
            Payment payment,
            boolean referenceMatched
    ) {
        if (!RECONCILIABLE_STATUSES.contains(payment.getStatus())) {
            return PaymentReconciliationMatchResult.unmatched(rowNumber, row);
        }

        BigDecimal variance = row.amount().subtract(payment.getAmount()).setScale(2, RoundingMode.HALF_UP);
        if (variance.compareTo(BigDecimal.ZERO.setScale(2)) != 0) {
            return PaymentReconciliationMatchResult.matched(
                    rowNumber,
                    row,
                    payment,
                    PaymentReconciliationMatchStatus.AMOUNT_VARIANCE,
                    variance,
                    "Referensi cocok, tetapi nominal bank berbeda dari payment."
            );
        }
        if (payment.getStatus() == PaymentStatus.REVERSED) {
            return PaymentReconciliationMatchResult.matched(
                    rowNumber,
                    row,
                    payment,
                    PaymentReconciliationMatchStatus.REVERSED_PAYMENT,
                    BigDecimal.ZERO.setScale(2),
                    "Payment sudah di-reverse; perlu pengecekan mutasi balik bank."
            );
        }

        return PaymentReconciliationMatchResult.matched(
                rowNumber,
                row,
                payment,
                referenceMatched ? PaymentReconciliationMatchStatus.EXACT_MATCH : PaymentReconciliationMatchStatus.PROBABLE_MATCH,
                BigDecimal.ZERO.setScale(2),
                referenceMatched ? "Referensi dan nominal cocok." : "Nominal, tanggal, dan channel cocok tanpa referensi eksplisit."
        );
    }

    private Optional<Payment> findReferenceMatch(String reference) {
        return paymentRepository.findByPaymentNumber(reference)
                .or(() -> paymentRepository.findByExternalReference(reference));
    }

    private static Specification<Payment> specification(PaymentReconciliationFilters filters) {
        return statusSpecification(filters.status())
                .and(channelSpecification(filters.channel()))
                .and(paidAtFromSpecification(filters.paidAtFrom()))
                .and(paidAtToSpecification(filters.paidAtTo()));
    }

    private static Specification<Payment> candidateSpecification(BankStatementRowCommand row) {
        LocalDate date = LocalDate.ofInstant(row.transactedAt(), ZoneOffset.UTC);
        Instant from = date.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        return statusSpecification(null)
                .and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("amount"), row.amount()))
                .and((root, query, criteriaBuilder) -> criteriaBuilder.greaterThanOrEqualTo(root.get("paidAt"), from))
                .and((root, query, criteriaBuilder) -> criteriaBuilder.lessThan(root.get("paidAt"), to))
                .and(channelSpecification(row.channel()));
    }

    private static Specification<Payment> statusSpecification(PaymentStatus status) {
        return (root, query, criteriaBuilder) -> {
            if (status == null) {
                return root.get("status").in(RECONCILIABLE_STATUSES);
            }
            return criteriaBuilder.equal(root.get("status"), status);
        };
    }

    private static Specification<Payment> channelSpecification(String channel) {
        return (root, query, criteriaBuilder) -> channel == null ? criteriaBuilder.conjunction() : criteriaBuilder.equal(root.get("channel"), channel);
    }

    private static Specification<Payment> paidAtFromSpecification(Instant paidAtFrom) {
        return (root, query, criteriaBuilder) -> paidAtFrom == null ? criteriaBuilder.conjunction() : criteriaBuilder.greaterThanOrEqualTo(root.get("paidAt"), paidAtFrom);
    }

    private static Specification<Payment> paidAtToSpecification(Instant paidAtTo) {
        return (root, query, criteriaBuilder) -> paidAtTo == null ? criteriaBuilder.conjunction() : criteriaBuilder.lessThanOrEqualTo(root.get("paidAt"), paidAtTo);
    }

    private static PaymentReconciliationFilters normalizeFilters(PaymentReconciliationFilters filters) {
        PaymentReconciliationFilters safeFilters = filters == null ? new PaymentReconciliationFilters(null, null, null, null) : filters;
        if (safeFilters.status() != null && !RECONCILIABLE_STATUSES.contains(safeFilters.status())) {
            throw new BusinessException("PAYMENT_RECONCILIATION_STATUS_INVALID", "Only settled or reversed payments can be exported for reconciliation.");
        }
        if (safeFilters.paidAtFrom() != null && safeFilters.paidAtTo() != null && safeFilters.paidAtTo().isBefore(safeFilters.paidAtFrom())) {
            throw new BusinessException("PAYMENT_RECONCILIATION_DATE_RANGE_INVALID", "Payment reconciliation date range is invalid.");
        }

        return new PaymentReconciliationFilters(
                safeFilters.status(),
                normalizeChannel(safeFilters.channel()),
                safeFilters.paidAtFrom(),
                safeFilters.paidAtTo()
        );
    }

    private static BankStatementRowCommand normalizeRow(BankStatementRowCommand row) {
        if (row == null) {
            throw new BusinessException("PAYMENT_RECONCILIATION_ROW_REQUIRED", "Bank statement row is required.");
        }
        String reference = requireNormalize(row.statementReference(), "PAYMENT_RECONCILIATION_REFERENCE_REQUIRED", "Bank statement reference is required.");
        BigDecimal amount = requirePositive(row.amount());
        if (row.transactedAt() == null) {
            throw new BusinessException("PAYMENT_RECONCILIATION_DATE_REQUIRED", "Bank statement transaction timestamp is required.");
        }

        return new BankStatementRowCommand(reference, amount, row.transactedAt(), normalizeChannel(row.channel()));
    }

    private static BigDecimal requirePositive(BigDecimal value) {
        if (value == null) {
            throw new BusinessException("PAYMENT_RECONCILIATION_AMOUNT_REQUIRED", "Bank statement amount is required.");
        }
        BigDecimal normalized = value.setScale(2, RoundingMode.HALF_UP);
        if (normalized.signum() <= 0) {
            throw new BusinessException("PAYMENT_RECONCILIATION_AMOUNT_INVALID", "Bank statement amount must be greater than zero.");
        }
        return normalized;
    }

    private static String requireNormalize(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    private static String normalizeChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return null;
        }
        return channel.trim().toUpperCase();
    }

    private static String auditReason(PaymentReconciliationFilters filters, int rowCount) {
        return "rows=" + rowCount
                + ";status=" + (filters.status() == null ? "SETTLED_OR_REVERSED" : filters.status())
                + ";channel=" + (filters.channel() == null ? "ALL" : filters.channel())
                + ";paidAtFrom=" + (filters.paidAtFrom() == null ? "-" : filters.paidAtFrom())
                + ";paidAtTo=" + (filters.paidAtTo() == null ? "-" : filters.paidAtTo());
    }
}
