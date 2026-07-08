package id.pdam.sia.reporting.application;

import id.pdam.sia.payment.domain.PaymentReconciliationSession;
import id.pdam.sia.payment.domain.PaymentReconciliationSessionStatus;
import id.pdam.sia.payment.repository.PaymentReconciliationSessionRepository;
import id.pdam.sia.reporting.domain.PaymentReconciliationHandoffNote;
import id.pdam.sia.reporting.domain.PaymentReconciliationHandoffNoteRevision;
import id.pdam.sia.reporting.repository.PaymentReconciliationHandoffNoteRepository;
import id.pdam.sia.reporting.repository.PaymentReconciliationHandoffNoteRevisionRepository;
import id.pdam.sia.shared.audit.AuditTrailService;
import id.pdam.sia.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BankReconciliationHandoffNoteApplicationService {
    private static final String PAYMENT_MODULE = "PAYMENT";
    private static final String CREATE_HANDOFF_NOTE_ACTION = "CREATE_RECONCILIATION_HANDOFF_NOTE";
    private static final String REVISE_HANDOFF_NOTE_ACTION = "REVISE_RECONCILIATION_HANDOFF_NOTE";

    private final PaymentReconciliationSessionRepository sessionRepository;
    private final PaymentReconciliationHandoffNoteRepository noteRepository;
    private final PaymentReconciliationHandoffNoteRevisionRepository revisionRepository;
    private final AuditTrailService auditTrailService;

    public BankReconciliationHandoffNoteApplicationService(
            PaymentReconciliationSessionRepository sessionRepository,
            PaymentReconciliationHandoffNoteRepository noteRepository,
            PaymentReconciliationHandoffNoteRevisionRepository revisionRepository,
            AuditTrailService auditTrailService
    ) {
        this.sessionRepository = sessionRepository;
        this.noteRepository = noteRepository;
        this.revisionRepository = revisionRepository;
        this.auditTrailService = auditTrailService;
    }

    @Transactional(readOnly = true)
    public List<PaymentReconciliationHandoffNoteEntry> handoffNotes(UUID sessionId) {
        PaymentReconciliationSession session = loadCompletedSession(sessionId);
        List<PaymentReconciliationHandoffNote> notes = noteRepository.findBySessionIdOrderByUpdatedAtDescCreatedAtDesc(
                session.getId()
        );
        if (notes.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<PaymentReconciliationHandoffNoteRevision>> revisionsByNote = revisionRepository
                .findByNoteIdInOrderByNoteIdAscRevisionNumberAsc(notes.stream().map(PaymentReconciliationHandoffNote::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(PaymentReconciliationHandoffNoteRevision::getNoteId));

        return notes.stream()
                .map(note -> PaymentReconciliationHandoffNoteEntry.from(
                        note,
                        revisionsByNote.getOrDefault(note.getId(), List.of())
                ))
                .toList();
    }

    @Transactional
    public PaymentReconciliationHandoffNoteEntry createNote(
            UUID sessionId,
            PaymentReconciliationHandoffNoteCommand command,
            String actor
    ) {
        PaymentReconciliationSession session = loadCompletedSession(sessionId);
        PaymentReconciliationHandoffNoteCommand safeCommand = requireCommand(command);
        PaymentReconciliationHandoffNote note = noteRepository.save(new PaymentReconciliationHandoffNote(
                session.getId(),
                safeCommand.noteText(),
                safeCommand.handoffOwner(),
                safeCommand.handoffDueDate(),
                safeCommand.handoffStatus(),
                actor
        ));
        PaymentReconciliationHandoffNoteRevision revision = revisionRepository.save(new PaymentReconciliationHandoffNoteRevision(
                note,
                1,
                safeCommand.reason(),
                actor
        ));
        auditTrailService.record(actor, PAYMENT_MODULE, CREATE_HANDOFF_NOTE_ACTION, note.getId().toString(), revision.getReason());
        return PaymentReconciliationHandoffNoteEntry.from(note, List.of(revision));
    }

    @Transactional
    public PaymentReconciliationHandoffNoteEntry reviseNote(
            UUID sessionId,
            UUID noteId,
            PaymentReconciliationHandoffNoteCommand command,
            String actor
    ) {
        PaymentReconciliationSession session = loadCompletedSession(sessionId);
        PaymentReconciliationHandoffNoteCommand safeCommand = requireCommand(command);
        PaymentReconciliationHandoffNote note = noteRepository.findBySessionIdAndId(session.getId(), noteId)
                .orElseThrow(() -> new BusinessException(
                        "PAYMENT_RECONCILIATION_HANDOFF_NOTE_NOT_FOUND",
                        "Reconciliation handoff note was not found."
                ));

        note.revise(
                safeCommand.noteText(),
                safeCommand.handoffOwner(),
                safeCommand.handoffDueDate(),
                safeCommand.handoffStatus(),
                actor
        );
        PaymentReconciliationHandoffNote savedNote = noteRepository.save(note);
        int nextRevisionNumber = Math.toIntExact(revisionRepository.countByNoteId(savedNote.getId()) + 1);
        PaymentReconciliationHandoffNoteRevision revision = revisionRepository.save(new PaymentReconciliationHandoffNoteRevision(
                savedNote,
                nextRevisionNumber,
                safeCommand.reason(),
                actor
        ));
        auditTrailService.record(actor, PAYMENT_MODULE, REVISE_HANDOFF_NOTE_ACTION, savedNote.getId().toString(), revision.getReason());

        return PaymentReconciliationHandoffNoteEntry.from(
                savedNote,
                revisionRepository.findByNoteIdOrderByRevisionNumberAsc(savedNote.getId())
        );
    }

    private PaymentReconciliationSession loadCompletedSession(UUID sessionId) {
        if (sessionId == null) {
            throw new BusinessException(
                    "PAYMENT_RECONCILIATION_SESSION_ID_REQUIRED",
                    "Reconciliation session id is required."
            );
        }
        PaymentReconciliationSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(
                        "PAYMENT_RECONCILIATION_SESSION_NOT_FOUND",
                        "Reconciliation session was not found."
                ));
        if (session.getStatus() != PaymentReconciliationSessionStatus.COMPLETED) {
            throw new BusinessException(
                    "PAYMENT_RECONCILIATION_HANDOFF_SESSION_NOT_COMPLETED",
                    "Only completed reconciliation sessions can receive handoff notes."
            );
        }
        return session;
    }

    private static PaymentReconciliationHandoffNoteCommand requireCommand(PaymentReconciliationHandoffNoteCommand command) {
        if (command == null) {
            throw new BusinessException(
                    "PAYMENT_RECONCILIATION_HANDOFF_COMMAND_REQUIRED",
                    "Reconciliation handoff note command is required."
            );
        }
        return command;
    }
}
