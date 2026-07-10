package id.pdam.sia.payment.application;

import id.pdam.sia.payment.domain.BankMutation;
import id.pdam.sia.payment.domain.BankMutationStatus;
import id.pdam.sia.payment.repository.BankMutationRepository;
import id.pdam.sia.shared.audit.AuditTrailService;
import id.pdam.sia.shared.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PaymentBankMutationApplicationService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_IMPORT_ROWS = 1_000;

    private final BankMutationRepository bankMutationRepository;
    private final PaymentReconciliationApplicationService reconciliationApplicationService;
    private final AuditTrailService auditTrailService;

    public PaymentBankMutationApplicationService(
            BankMutationRepository bankMutationRepository,
            PaymentReconciliationApplicationService reconciliationApplicationService,
            AuditTrailService auditTrailService
    ) {
        this.bankMutationRepository = bankMutationRepository;
        this.reconciliationApplicationService = reconciliationApplicationService;
        this.auditTrailService = auditTrailService;
    }

    @Transactional(readOnly = true)
    public Page<BankMutation> listMutations(BankMutationStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "transactedAt").and(Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        if (status == null) {
            return bankMutationRepository.findAll(pageable);
        }
        return bankMutationRepository.findByStatus(status, pageable);
    }

    @Transactional
    public BankMutationImportResult importMutations(ImportBankMutationsCommand command, String actor) {
        if (command.rows() == null || command.rows().isEmpty()) {
            throw new BusinessException("BANK_MUTATION_ROWS_REQUIRED", "Bank mutation rows are required.");
        }
        if (command.rows().size() > MAX_IMPORT_ROWS) {
            throw new BusinessException("BANK_MUTATION_ROWS_LIMIT", "Bank mutation import cannot exceed 1000 rows.");
        }
        String sourceFilename = requireNormalize(command.sourceFilename(), "BANK_MUTATION_SOURCE_REQUIRED", "Bank mutation source filename is required.");
        String bankAccountReference = requireNormalize(command.bankAccountReference(), "BANK_MUTATION_ACCOUNT_REQUIRED", "Bank account reference is required.");
        List<BankMutation> mutations = command.rows().stream()
                .map(row -> {
                    String reference = requireNormalize(row.externalReference(), "BANK_MUTATION_REFERENCE_REQUIRED", "Bank mutation reference is required.");
                    if (bankMutationRepository.existsByExternalReference(reference)) {
                        throw new BusinessException("BANK_MUTATION_REFERENCE_DUPLICATE", "Bank mutation reference already exists.");
                    }
                    return new BankMutation(
                            reference,
                            sourceFilename,
                            bankAccountReference,
                            row.amount(),
                            row.transactedAt(),
                            row.channel(),
                            row.description()
                    );
                })
                .toList();
        List<BankMutation> saved = bankMutationRepository.saveAll(mutations);
        auditTrailService.record(actor, "PAYMENT", "IMPORT_BANK_MUTATIONS", sourceFilename, "rows=" + saved.size());
        return new BankMutationImportResult(saved.size(), saved);
    }

    @Transactional
    public BankMutationReconciliationResult reconcileDaily(ReconcileBankDailyCommand command, String actor) {
        LocalDate date = command.date();
        if (date == null) {
            throw new BusinessException("BANK_RECONCILIATION_DATE_REQUIRED", "Bank reconciliation date is required.");
        }
        Instant from = date.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        List<BankMutation> mutations = bankMutationRepository
                .findByStatusAndTransactedAtGreaterThanEqualAndTransactedAtLessThanOrderByTransactedAtAsc(
                        BankMutationStatus.UNMATCHED,
                        from,
                        to
                );
        if (mutations.isEmpty()) {
            throw new BusinessException("BANK_RECONCILIATION_NO_MUTATIONS", "No unmatched bank mutations were found for this date.");
        }

        List<BankStatementRowCommand> rows = mutations.stream()
                .map(mutation -> new BankStatementRowCommand(
                        mutation.getExternalReference(),
                        mutation.getAmount(),
                        mutation.getTransactedAt(),
                        mutation.getChannel()
                ))
                .toList();
        PaymentReconciliationSessionResult sessionResult = reconciliationApplicationService.createSession(
                rows,
                command.sourceFilename() == null ? "daily-bank-mutations-" + date + ".csv" : command.sourceFilename(),
                command.bankAccountReference(),
                actor
        );

        Map<Integer, BankMutation> mutationByRow = java.util.stream.IntStream.range(0, mutations.size())
                .boxed()
                .collect(Collectors.toMap(index -> index + 1, index -> mutations.get(index)));
        int matched = 0;
        int exceptions = 0;
        for (var item : sessionResult.items()) {
            BankMutation mutation = mutationByRow.get(item.getRowNumber());
            if (mutation == null) {
                continue;
            }
            if (item.getMatchedPaymentId() != null
                    && (item.getMatchStatus() == PaymentReconciliationMatchStatus.EXACT_MATCH
                    || item.getMatchStatus() == PaymentReconciliationMatchStatus.PROBABLE_MATCH)) {
                mutation.markMatched(sessionResult.session().getId(), item.getMatchedPaymentId());
                matched++;
            } else {
                mutation.linkSession(sessionResult.session().getId());
                exceptions++;
            }
            bankMutationRepository.save(mutation);
        }
        auditTrailService.record(
                actor,
                "PAYMENT",
                "RECONCILE_BANK_DAILY",
                sessionResult.session().getId().toString(),
                "date=" + date + ";rows=" + mutations.size() + ";matched=" + matched + ";exceptions=" + exceptions
        );
        return new BankMutationReconciliationResult(sessionResult.session().getId(), mutations.size(), matched, exceptions);
    }

    @Transactional
    public BankMutation resolveMutation(UUID mutationId, ResolveBankMutationCommand command, String actor) {
        BankMutation mutation = bankMutationRepository.findById(mutationId)
                .orElseThrow(() -> new BusinessException("BANK_MUTATION_NOT_FOUND", "Bank mutation was not found."));
        mutation.resolve(command.reason(), actor);
        BankMutation saved = bankMutationRepository.save(mutation);
        auditTrailService.record(actor, "PAYMENT", "RESOLVE_BANK_MUTATION", saved.getId().toString(), command.reason());
        return saved;
    }

    private static String requireNormalize(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    public record ImportBankMutationsCommand(String sourceFilename, String bankAccountReference, List<BankMutationRowCommand> rows) {
    }

    public record BankMutationRowCommand(
            String externalReference,
            BigDecimal amount,
            Instant transactedAt,
            String channel,
            String description
    ) {
    }

    public record ReconcileBankDailyCommand(LocalDate date, String sourceFilename, String bankAccountReference) {
    }

    public record ResolveBankMutationCommand(String reason) {
    }

    public record BankMutationImportResult(int importedRows, List<BankMutation> mutations) {
        public BankMutationImportResult {
            mutations = List.copyOf(mutations);
        }
    }

    public record BankMutationReconciliationResult(UUID sessionId, int totalRows, int matchedRows, int exceptionRows) {
    }
}
