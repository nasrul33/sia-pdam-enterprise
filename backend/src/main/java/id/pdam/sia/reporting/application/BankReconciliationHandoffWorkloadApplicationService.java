package id.pdam.sia.reporting.application;

import id.pdam.sia.payment.domain.PaymentReconciliationSession;
import id.pdam.sia.payment.repository.PaymentReconciliationSessionRepository;
import id.pdam.sia.reporting.domain.PaymentReconciliationHandoffNote;
import id.pdam.sia.reporting.domain.PaymentReconciliationHandoffStatus;
import id.pdam.sia.reporting.repository.PaymentReconciliationHandoffNoteRepository;
import id.pdam.sia.reporting.repository.PaymentReconciliationHandoffNoteRevisionCount;
import id.pdam.sia.reporting.repository.PaymentReconciliationHandoffNoteRevisionRepository;
import id.pdam.sia.shared.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BankReconciliationHandoffWorkloadApplicationService {
    static final String UNASSIGNED_OWNER_KEY = "__UNASSIGNED__";

    private static final int MAX_EXPORT_ROWS = 10_000;
    private static final Sort WORKLOAD_SORT = Sort.by(Sort.Direction.ASC, "handoffDueDate")
            .and(Sort.by(Sort.Direction.ASC, "handoffStatus"))
            .and(Sort.by(Sort.Direction.DESC, "updatedAt"));

    private final PaymentReconciliationHandoffNoteRepository noteRepository;
    private final PaymentReconciliationHandoffNoteRevisionRepository revisionRepository;
    private final PaymentReconciliationSessionRepository sessionRepository;

    public BankReconciliationHandoffWorkloadApplicationService(
            PaymentReconciliationHandoffNoteRepository noteRepository,
            PaymentReconciliationHandoffNoteRevisionRepository revisionRepository,
            PaymentReconciliationSessionRepository sessionRepository
    ) {
        this.noteRepository = noteRepository;
        this.revisionRepository = revisionRepository;
        this.sessionRepository = sessionRepository;
    }

    @Transactional(readOnly = true)
    public Page<PaymentReconciliationHandoffWorkloadEntry> workload(
            PaymentReconciliationHandoffWorkloadFilters filters,
            int page,
            int size
    ) {
        PaymentReconciliationHandoffWorkloadFilters normalized = normalize(filters);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), WORKLOAD_SORT);
        return loadWorkload(normalized, pageable);
    }

    @Transactional(readOnly = true)
    public String workloadCsv(PaymentReconciliationHandoffWorkloadFilters filters) {
        Page<PaymentReconciliationHandoffWorkloadEntry> rows = loadWorkload(
                normalize(filters),
                PageRequest.of(0, MAX_EXPORT_ROWS, WORKLOAD_SORT)
        );
        StringBuilder builder = new StringBuilder();
        appendRow(
                builder,
                "note_id",
                "session_id",
                "session_number",
                "bank_account_reference",
                "completed_at",
                "review_status",
                "signed_off_by",
                "signed_off_at",
                "handoff_status",
                "handoff_owner",
                "handoff_due_date",
                "overdue_days",
                "revision_count",
                "reviewer_notes",
                "updated_by",
                "updated_at",
                "generated_at"
        );
        rows.getContent().forEach(row -> appendRow(
                builder,
                row.noteId(),
                row.sessionId(),
                row.sessionNumber(),
                row.bankAccountReference(),
                row.completedAt(),
                row.reviewStatus(),
                row.signedOffBy(),
                row.signedOffAt(),
                row.handoffStatus(),
                row.handoffOwner(),
                row.handoffDueDate(),
                row.overdueDays(),
                row.revisionCount(),
                row.noteText(),
                row.updatedBy(),
                row.updatedAt(),
                row.generatedAt()
        ));
        return builder.toString();
    }

    @Transactional(readOnly = true)
    public PaymentReconciliationHandoffOwnerSlaReport ownerSla(PaymentReconciliationHandoffWorkloadFilters filters) {
        PaymentReconciliationHandoffWorkloadFilters normalized = normalize(filters);
        Page<PaymentReconciliationHandoffNote> notes = loadNotes(
                normalized,
                PageRequest.of(0, MAX_EXPORT_ROWS, WORKLOAD_SORT)
        );
        Instant generatedAt = Instant.now();
        LocalDate generatedDate = LocalDate.now(ZoneOffset.UTC);
        List<PaymentReconciliationHandoffOwnerSlaEntry> owners = notes.getContent().stream()
                .collect(Collectors.groupingBy(BankReconciliationHandoffWorkloadApplicationService::ownerKey))
                .entrySet()
                .stream()
                .map(entry -> PaymentReconciliationHandoffOwnerSlaEntry.from(
                        entry.getKey(),
                        entry.getValue(),
                        generatedDate,
                        generatedAt
                ))
                .sorted(ownerSlaSort())
                .toList();
        return new PaymentReconciliationHandoffOwnerSlaReport(
                owners,
                owners.stream().mapToLong(PaymentReconciliationHandoffOwnerSlaEntry::totalNotes).sum(),
                owners.stream().mapToLong(PaymentReconciliationHandoffOwnerSlaEntry::openNotes).sum(),
                owners.stream().mapToLong(PaymentReconciliationHandoffOwnerSlaEntry::inProgressNotes).sum(),
                owners.stream().mapToLong(PaymentReconciliationHandoffOwnerSlaEntry::clearedNotes).sum(),
                owners.stream().mapToLong(PaymentReconciliationHandoffOwnerSlaEntry::overdueNotes).sum(),
                notes.getTotalElements() > notes.getContent().size(),
                generatedAt
        );
    }

    @Transactional(readOnly = true)
    public String ownerSlaCsv(PaymentReconciliationHandoffWorkloadFilters filters) {
        PaymentReconciliationHandoffOwnerSlaReport report = ownerSla(filters);
        StringBuilder builder = new StringBuilder();
        appendRow(
                builder,
                "owner",
                "unassigned",
                "total_notes",
                "open_notes",
                "in_progress_notes",
                "cleared_notes",
                "overdue_notes",
                "nearest_due_date",
                "max_overdue_days",
                "escalation_priority",
                "latest_updated_at",
                "generated_at"
        );
        report.owners().forEach(owner -> appendRow(
                builder,
                owner.ownerLabel(),
                owner.unassigned(),
                owner.totalNotes(),
                owner.openNotes(),
                owner.inProgressNotes(),
                owner.clearedNotes(),
                owner.overdueNotes(),
                owner.nearestDueDate(),
                owner.maxOverdueDays(),
                owner.escalationPriority(),
                owner.latestUpdatedAt(),
                owner.generatedAt()
        ));
        return builder.toString();
    }

    private Page<PaymentReconciliationHandoffWorkloadEntry> loadWorkload(
            PaymentReconciliationHandoffWorkloadFilters filters,
            PageRequest pageable
    ) {
        Page<PaymentReconciliationHandoffNote> notes = loadNotes(filters, pageable);
        List<UUID> sessionIds = notes.getContent().stream()
                .map(PaymentReconciliationHandoffNote::getSessionId)
                .distinct()
                .toList();
        Map<UUID, PaymentReconciliationSession> sessionsById = sessionIds.isEmpty()
                ? Map.of()
                : sessionRepository.findAllById(sessionIds)
                        .stream()
                        .collect(Collectors.toMap(PaymentReconciliationSession::getId, Function.identity()));
        List<UUID> noteIds = notes.getContent().stream()
                .map(PaymentReconciliationHandoffNote::getId)
                .toList();
        Map<UUID, Long> revisionCounts = noteIds.isEmpty()
                ? Map.of()
                : revisionRepository.countRevisionsByNoteIdIn(noteIds)
                        .stream()
                        .collect(Collectors.toMap(
                                PaymentReconciliationHandoffNoteRevisionCount::getNoteId,
                                PaymentReconciliationHandoffNoteRevisionCount::getRevisionCount
                        ));
        Instant generatedAt = Instant.now();
        LocalDate generatedDate = LocalDate.now(ZoneOffset.UTC);

        List<PaymentReconciliationHandoffWorkloadEntry> entries = notes.getContent().stream()
                .map(note -> PaymentReconciliationHandoffWorkloadEntry.from(
                        note,
                        requireSession(sessionsById, note.getSessionId()),
                        revisionCounts.getOrDefault(note.getId(), 0L),
                        generatedDate,
                        generatedAt
                ))
                .toList();

        return new PageImpl<>(entries, pageable, notes.getTotalElements());
    }

    private Page<PaymentReconciliationHandoffNote> loadNotes(
            PaymentReconciliationHandoffWorkloadFilters filters,
            PageRequest pageable
    ) {
        return noteRepository.findAll(specification(filters), pageable);
    }

    private static PaymentReconciliationHandoffWorkloadFilters normalize(
            PaymentReconciliationHandoffWorkloadFilters filters
    ) {
        PaymentReconciliationHandoffWorkloadFilters safeFilters = filters == null
                ? new PaymentReconciliationHandoffWorkloadFilters(null, null, false, null, null)
                : filters;
        String normalizedOwner = normalizeOwner(safeFilters.handoffOwner());
        if (safeFilters.unassignedOnly() && normalizedOwner != null) {
            throw new BusinessException(
                    "PAYMENT_RECONCILIATION_HANDOFF_OWNER_FILTER_CONFLICT",
                    "Reconciliation handoff owner filter conflicts with unassigned-only scope."
            );
        }
        if (safeFilters.dueFrom() != null
                && safeFilters.dueTo() != null
                && safeFilters.dueTo().isBefore(safeFilters.dueFrom())) {
            throw new BusinessException(
                    "PAYMENT_RECONCILIATION_HANDOFF_DUE_RANGE_INVALID",
                    "Reconciliation handoff due date range is invalid."
            );
        }
        return new PaymentReconciliationHandoffWorkloadFilters(
                safeFilters.handoffStatus(),
                normalizedOwner,
                safeFilters.unassignedOnly(),
                safeFilters.dueFrom(),
                safeFilters.dueTo()
        );
    }

    private static String normalizeOwner(String owner) {
        if (owner == null || owner.isBlank()) {
            return null;
        }
        return owner.trim().toLowerCase();
    }

    private static PaymentReconciliationSession requireSession(
            Map<UUID, PaymentReconciliationSession> sessionsById,
            UUID sessionId
    ) {
        PaymentReconciliationSession session = sessionsById.get(sessionId);
        if (session == null) {
            throw new BusinessException(
                    "PAYMENT_RECONCILIATION_SESSION_NOT_FOUND",
                    "Reconciliation session was not found."
            );
        }
        return session;
    }

    private static Specification<PaymentReconciliationHandoffNote> specification(
            PaymentReconciliationHandoffWorkloadFilters filters
    ) {
        return statusSpecification(filters.handoffStatus())
                .and(ownerSpecification(filters.handoffOwner(), filters.unassignedOnly()))
                .and(dueFromSpecification(filters.dueFrom()))
                .and(dueToSpecification(filters.dueTo()));
    }

    private static Specification<PaymentReconciliationHandoffNote> statusSpecification(
            PaymentReconciliationHandoffStatus status
    ) {
        return (root, query, criteriaBuilder) -> status == null
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.equal(root.get("handoffStatus"), status);
    }

    private static Specification<PaymentReconciliationHandoffNote> ownerSpecification(String owner, boolean unassignedOnly) {
        return (root, query, criteriaBuilder) -> {
            if (unassignedOnly) {
                return criteriaBuilder.isNull(root.get("handoffOwner"));
            }
            return owner == null
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.like(criteriaBuilder.lower(root.get("handoffOwner")), "%" + owner + "%");
        };
    }

    private static Specification<PaymentReconciliationHandoffNote> dueFromSpecification(LocalDate dueFrom) {
        return (root, query, criteriaBuilder) -> dueFrom == null
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.greaterThanOrEqualTo(root.get("handoffDueDate"), dueFrom);
    }

    private static Specification<PaymentReconciliationHandoffNote> dueToSpecification(LocalDate dueTo) {
        return (root, query, criteriaBuilder) -> dueTo == null
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.lessThanOrEqualTo(root.get("handoffDueDate"), dueTo);
    }

    static long overdueDays(PaymentReconciliationHandoffNote note, LocalDate generatedDate) {
        if (note.getHandoffStatus() == PaymentReconciliationHandoffStatus.CLEARED || note.getHandoffDueDate() == null) {
            return 0;
        }
        if (!note.getHandoffDueDate().isBefore(generatedDate)) {
            return 0;
        }
        return Math.max(0, ChronoUnit.DAYS.between(note.getHandoffDueDate(), generatedDate));
    }

    private static String ownerKey(PaymentReconciliationHandoffNote note) {
        return note.getHandoffOwner() == null ? UNASSIGNED_OWNER_KEY : note.getHandoffOwner();
    }

    private static Comparator<PaymentReconciliationHandoffOwnerSlaEntry> ownerSlaSort() {
        return Comparator.comparingLong(PaymentReconciliationHandoffOwnerSlaEntry::maxOverdueDays)
                .reversed()
                .thenComparing(Comparator.comparingLong(PaymentReconciliationHandoffOwnerSlaEntry::overdueNotes).reversed())
                .thenComparing(Comparator.comparingLong((PaymentReconciliationHandoffOwnerSlaEntry owner) ->
                        owner.openNotes() + owner.inProgressNotes()).reversed())
                .thenComparing(PaymentReconciliationHandoffOwnerSlaEntry::ownerLabel);
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
}
