package id.pdam.sia.reporting.application;

import id.pdam.sia.payment.domain.PaymentReconciliationItem;
import id.pdam.sia.payment.domain.PaymentReconciliationSession;
import id.pdam.sia.payment.domain.PaymentReconciliationSessionStatus;
import id.pdam.sia.payment.repository.PaymentReconciliationItemRepository;
import id.pdam.sia.payment.repository.PaymentReconciliationSessionRepository;
import id.pdam.sia.shared.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BankReconciliationReviewRegisterApplicationService {
    private static final int MAX_EXPORT_ROWS = 10_000;
    private static final Sort REVIEW_SORT = Sort.by(Sort.Direction.DESC, "completedAt")
            .and(Sort.by(Sort.Direction.DESC, "startedAt"))
            .and(Sort.by(Sort.Direction.DESC, "createdAt"));

    private final PaymentReconciliationSessionRepository sessionRepository;
    private final PaymentReconciliationItemRepository itemRepository;

    public BankReconciliationReviewRegisterApplicationService(
            PaymentReconciliationSessionRepository sessionRepository,
            PaymentReconciliationItemRepository itemRepository
    ) {
        this.sessionRepository = sessionRepository;
        this.itemRepository = itemRepository;
    }

    @Transactional(readOnly = true)
    public Page<BankReconciliationReviewRegisterEntry> reviewRegister(
            BankReconciliationReviewRegisterFilters filters,
            int page,
            int size
    ) {
        BankReconciliationReviewRegisterFilters normalized = normalize(filters);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), REVIEW_SORT);
        return loadReviewRegister(normalized, pageable);
    }

    @Transactional(readOnly = true)
    public String reviewRegisterCsv(BankReconciliationReviewRegisterFilters filters) {
        Page<BankReconciliationReviewRegisterEntry> rows = loadReviewRegister(
                normalize(filters),
                PageRequest.of(0, MAX_EXPORT_ROWS, REVIEW_SORT)
        );
        StringBuilder builder = new StringBuilder();
        appendRow(
                builder,
                "session_id",
                "session_number",
                "review_status",
                "bank_account_reference",
                "source_filename",
                "created_by",
                "started_at",
                "completed_at",
                "signed_off_by",
                "signed_off_at",
                "sign_off_reason",
                "total_rows",
                "exception_items",
                "amount_variances",
                "reversed_payments",
                "multiple_candidates",
                "unmatched_rows",
                "accepted_items",
                "resolved_items",
                "ignored_items",
                "adjusted_items",
                "total_variance",
                "pending_sign_off_age_days",
                "generated_at",
                "reviewer_notes",
                "handoff_owner",
                "handoff_due_date",
                "handoff_status"
        );
        rows.getContent().forEach(row -> appendRow(
                builder,
                row.sessionId(),
                row.sessionNumber(),
                row.reviewStatus(),
                row.bankAccountReference(),
                row.sourceFilename(),
                row.createdBy(),
                row.startedAt(),
                row.completedAt(),
                row.signedOffBy(),
                row.signedOffAt(),
                row.signOffReason(),
                row.totalRows(),
                row.exceptionItems(),
                row.amountVariances(),
                row.reversedPayments(),
                row.multipleCandidates(),
                row.unmatchedRows(),
                row.acceptedItems(),
                row.resolvedItems(),
                row.ignoredItems(),
                row.adjustedItems(),
                row.totalVariance(),
                row.pendingSignOffAgeDays(),
                row.generatedAt(),
                "",
                "",
                "",
                ""
        ));
        return builder.toString();
    }

    private Page<BankReconciliationReviewRegisterEntry> loadReviewRegister(
            BankReconciliationReviewRegisterFilters normalized,
            PageRequest pageable
    ) {
        Page<PaymentReconciliationSession> sessions = sessionRepository.findAll(specification(normalized), pageable);
        List<UUID> sessionIds = sessions.getContent().stream().map(PaymentReconciliationSession::getId).toList();
        Map<UUID, List<PaymentReconciliationItem>> itemsBySession = sessionIds.isEmpty()
                ? Map.of()
                : itemRepository
                        .findBySessionIdInOrderBySessionIdAscRowNumberAsc(sessionIds)
                        .stream()
                        .collect(Collectors.groupingBy(PaymentReconciliationItem::getSessionId));
        Instant generatedAt = Instant.now();

        List<BankReconciliationReviewRegisterEntry> entries = sessions.getContent().stream()
                .map(session -> BankReconciliationReviewRegisterEntry.from(
                        session,
                        itemsBySession.getOrDefault(session.getId(), List.of()),
                        generatedAt
                ))
                .toList();

        return new PageImpl<>(entries, pageable, sessions.getTotalElements());
    }

    private static void appendRow(StringBuilder builder, Object... values) {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            appendValue(builder, values[index]);
        }
        builder.append('\n');
    }

    private static void appendValue(StringBuilder builder, Object value) {
        if (value == null) {
            return;
        }
        String text = value.toString();
        boolean quoted = text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r");
        if (!quoted) {
            builder.append(text);
            return;
        }

        builder.append('"');
        builder.append(text.replace("\"", "\"\""));
        builder.append('"');
    }

    private static BankReconciliationReviewRegisterFilters normalize(BankReconciliationReviewRegisterFilters filters) {
        BankReconciliationReviewRegisterFilters safeFilters = filters == null
                ? new BankReconciliationReviewRegisterFilters(null, null, null)
                : filters;
        if (safeFilters.completedFrom() != null
                && safeFilters.completedTo() != null
                && safeFilters.completedTo().isBefore(safeFilters.completedFrom())) {
            throw new BusinessException(
                    "PAYMENT_RECONCILIATION_REVIEW_DATE_RANGE_INVALID",
                    "Reconciliation review date range is invalid."
            );
        }
        return safeFilters;
    }

    private static Specification<PaymentReconciliationSession> specification(BankReconciliationReviewRegisterFilters filters) {
        return completedSessionSpecification()
                .and(signOffStatusSpecification(filters.signOffStatus()))
                .and(completedFromSpecification(filters.completedFrom()))
                .and(completedToSpecification(filters.completedTo()));
    }

    private static Specification<PaymentReconciliationSession> completedSessionSpecification() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(
                root.get("status"),
                PaymentReconciliationSessionStatus.COMPLETED
        );
    }

    private static Specification<PaymentReconciliationSession> signOffStatusSpecification(
            PaymentReconciliationReviewStatus signOffStatus
    ) {
        return (root, query, criteriaBuilder) -> {
            if (signOffStatus == null) {
                return criteriaBuilder.conjunction();
            }
            if (signOffStatus == PaymentReconciliationReviewStatus.SIGNED_OFF) {
                return criteriaBuilder.isNotNull(root.get("signedOffAt"));
            }
            return criteriaBuilder.isNull(root.get("signedOffAt"));
        };
    }

    private static Specification<PaymentReconciliationSession> completedFromSpecification(Instant completedFrom) {
        return (root, query, criteriaBuilder) -> completedFrom == null
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.greaterThanOrEqualTo(root.get("completedAt"), completedFrom);
    }

    private static Specification<PaymentReconciliationSession> completedToSpecification(Instant completedTo) {
        return (root, query, criteriaBuilder) -> completedTo == null
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.lessThanOrEqualTo(root.get("completedAt"), completedTo);
    }
}
