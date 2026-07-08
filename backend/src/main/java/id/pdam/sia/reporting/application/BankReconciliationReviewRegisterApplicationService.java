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
