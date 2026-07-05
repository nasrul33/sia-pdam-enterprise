package id.pdam.sia.receivable.application;

import id.pdam.sia.billing.domain.Invoice;
import id.pdam.sia.billing.domain.InvoiceStatus;
import id.pdam.sia.billing.repository.InvoiceRepository;
import id.pdam.sia.receivable.domain.ReceivableAgingSnapshot;
import id.pdam.sia.receivable.repository.ReceivableAgingSnapshotRepository;
import id.pdam.sia.receivable.web.GenerateReceivableAgingSnapshotRequest;
import id.pdam.sia.shared.audit.AuditTrailService;
import id.pdam.sia.shared.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class ReceivableAgingApplicationService {
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM");
    private static final int MAX_PAGE_SIZE = 100;

    private final InvoiceRepository invoiceRepository;
    private final ReceivableAgingSnapshotRepository snapshotRepository;
    private final AuditTrailService auditTrailService;

    public ReceivableAgingApplicationService(
            InvoiceRepository invoiceRepository,
            ReceivableAgingSnapshotRepository snapshotRepository,
            AuditTrailService auditTrailService
    ) {
        this.invoiceRepository = invoiceRepository;
        this.snapshotRepository = snapshotRepository;
        this.auditTrailService = auditTrailService;
    }

    @Transactional(readOnly = true)
    public Page<ReceivableAgingSnapshot> listSnapshots(int page, int size) {
        return snapshotRepository.findAll(pageable(page, size, Sort.by("generatedAt").descending()));
    }

    @Transactional(readOnly = true)
    public ReceivableAgingSnapshot getSnapshot(UUID snapshotId) {
        return snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new BusinessException("RECEIVABLE_AGING_NOT_FOUND", "Receivable aging snapshot was not found."));
    }

    @Transactional(readOnly = true)
    public ReceivableAgingSnapshot getSnapshotByPeriod(String period) {
        String normalizedPeriod = normalizePeriod(period);
        return snapshotRepository.findByPeriod(normalizedPeriod)
                .orElseThrow(() -> new BusinessException("RECEIVABLE_AGING_NOT_FOUND", "Receivable aging snapshot was not found."));
    }

    @Transactional
    public ReceivableAgingSnapshot generateSnapshot(GenerateReceivableAgingSnapshotRequest request, String actor) {
        if (request == null) {
            throw new BusinessException("RECEIVABLE_AGING_REQUEST_REQUIRED", "Receivable aging request is required.");
        }
        String period = normalizePeriod(request.period());
        LocalDate asOfDate = requireAsOfDate(request.asOfDate());
        String reason = requireNormalize(request.reason(), "RECEIVABLE_AGING_REASON_REQUIRED", "Receivable aging reason is required.");

        AgingBuckets buckets = invoiceRepository.findOpenReceivables().stream()
                .filter(ReceivableAgingApplicationService::isOpenReceivable)
                .reduce(
                        AgingBuckets.zero(),
                        (current, invoice) -> current.plus(invoice, asOfDate),
                        AgingBuckets::plus
                );

        ReceivableAgingSnapshot snapshot = snapshotRepository.findByPeriod(period)
                .orElseGet(() -> new ReceivableAgingSnapshot(
                        period,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        Instant.now()
                ));
        snapshot.replaceAmounts(
                buckets.currentAmount(),
                buckets.bucket30Amount(),
                buckets.bucket60Amount(),
                buckets.bucket90Amount(),
                buckets.bucketOver90Amount(),
                Instant.now()
        );
        ReceivableAgingSnapshot saved = snapshotRepository.save(snapshot);
        auditTrailService.record(actor, "RECEIVABLE", "GENERATE_RECEIVABLE_AGING", saved.getId().toString(), reason);
        return saved;
    }

    private static boolean isOpenReceivable(Invoice invoice) {
        if (invoice == null || invoice.getOutstandingAmount() == null || invoice.getDueDate() == null) {
            return false;
        }
        return (invoice.getStatus() == InvoiceStatus.ISSUED || invoice.getStatus() == InvoiceStatus.PARTIAL_PAID)
                && invoice.getOutstandingAmount().signum() > 0;
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

    private static String normalizePeriod(String period) {
        String normalized = requireNormalize(period, "RECEIVABLE_AGING_PERIOD_REQUIRED", "Receivable aging period is required.");
        try {
            YearMonth.parse(normalized, PERIOD_FORMATTER);
        } catch (DateTimeException exception) {
            throw new BusinessException("RECEIVABLE_AGING_PERIOD_INVALID", "Receivable aging period must use yyyy-MM format.");
        }
        return normalized;
    }

    private static LocalDate requireAsOfDate(LocalDate asOfDate) {
        if (asOfDate == null) {
            throw new BusinessException("RECEIVABLE_AGING_AS_OF_DATE_REQUIRED", "Receivable aging as-of date is required.");
        }
        return asOfDate;
    }

    private static String requireNormalize(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    private record AgingBuckets(
            BigDecimal currentAmount,
            BigDecimal bucket30Amount,
            BigDecimal bucket60Amount,
            BigDecimal bucket90Amount,
            BigDecimal bucketOver90Amount
    ) {
        private static AgingBuckets zero() {
            BigDecimal zero = BigDecimal.ZERO.setScale(2);
            return new AgingBuckets(zero, zero, zero, zero, zero);
        }

        private AgingBuckets plus(Invoice invoice, LocalDate asOfDate) {
            BigDecimal amount = invoice.getOutstandingAmount();
            long daysOverdue = ChronoUnit.DAYS.between(invoice.getDueDate(), asOfDate);
            if (daysOverdue <= 0) {
                return new AgingBuckets(currentAmount.add(amount), bucket30Amount, bucket60Amount, bucket90Amount, bucketOver90Amount);
            }
            if (daysOverdue <= 30) {
                return new AgingBuckets(currentAmount, bucket30Amount.add(amount), bucket60Amount, bucket90Amount, bucketOver90Amount);
            }
            if (daysOverdue <= 60) {
                return new AgingBuckets(currentAmount, bucket30Amount, bucket60Amount.add(amount), bucket90Amount, bucketOver90Amount);
            }
            if (daysOverdue <= 90) {
                return new AgingBuckets(currentAmount, bucket30Amount, bucket60Amount, bucket90Amount.add(amount), bucketOver90Amount);
            }
            return new AgingBuckets(currentAmount, bucket30Amount, bucket60Amount, bucket90Amount, bucketOver90Amount.add(amount));
        }

        private AgingBuckets plus(AgingBuckets other) {
            return new AgingBuckets(
                    currentAmount.add(other.currentAmount),
                    bucket30Amount.add(other.bucket30Amount),
                    bucket60Amount.add(other.bucket60Amount),
                    bucket90Amount.add(other.bucket90Amount),
                    bucketOver90Amount.add(other.bucketOver90Amount)
            );
        }
    }
}
