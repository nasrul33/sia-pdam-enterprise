package id.pdam.sia.reporting.application;

import id.pdam.sia.payment.application.PaymentReconciliationMatchSummary;
import id.pdam.sia.payment.domain.PaymentReconciliationSession;
import id.pdam.sia.payment.repository.PaymentReconciliationSessionRepository;
import id.pdam.sia.reporting.domain.PaymentReconciliationHandoffNote;
import id.pdam.sia.reporting.domain.PaymentReconciliationHandoffNoteRevision;
import id.pdam.sia.reporting.domain.PaymentReconciliationHandoffStatus;
import id.pdam.sia.reporting.repository.PaymentReconciliationHandoffNoteRepository;
import id.pdam.sia.reporting.repository.PaymentReconciliationHandoffNoteRevisionRepository;
import id.pdam.sia.shared.audit.AuditTrailService;
import id.pdam.sia.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BankReconciliationHandoffNoteApplicationServiceTest {
    private final PaymentReconciliationSessionRepository sessionRepository = mock(PaymentReconciliationSessionRepository.class);
    private final PaymentReconciliationHandoffNoteRepository noteRepository =
            mock(PaymentReconciliationHandoffNoteRepository.class);
    private final PaymentReconciliationHandoffNoteRevisionRepository revisionRepository =
            mock(PaymentReconciliationHandoffNoteRevisionRepository.class);
    private final AuditTrailService auditTrailService = mock(AuditTrailService.class);
    private final BankReconciliationHandoffNoteApplicationService service =
            new BankReconciliationHandoffNoteApplicationService(
                    sessionRepository,
                    noteRepository,
                    revisionRepository,
                    auditTrailService
            );

    @Test
    void createsHandoffNoteWithInitialRevisionAndAuditTrail() {
        PaymentReconciliationSession session = completedSession("REC-20260801-HANDOFF");
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(noteRepository.save(any(PaymentReconciliationHandoffNote.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(revisionRepository.save(any(PaymentReconciliationHandoffNoteRevision.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentReconciliationHandoffNoteEntry result = service.createNote(
                session.getId(),
                new PaymentReconciliationHandoffNoteCommand(
                        "  Cek settlement provider yang belum ada bukti mutasi. ",
                        " finance.ops ",
                        LocalDate.parse("2026-08-03"),
                        PaymentReconciliationHandoffStatus.IN_PROGRESS,
                        "Dibuat setelah review register harian."
                ),
                "auditor.internal"
        );

        assertThat(result.sessionId()).isEqualTo(session.getId());
        assertThat(result.noteText()).isEqualTo("Cek settlement provider yang belum ada bukti mutasi.");
        assertThat(result.handoffOwner()).isEqualTo("finance.ops");
        assertThat(result.handoffDueDate()).isEqualTo(LocalDate.parse("2026-08-03"));
        assertThat(result.handoffStatus()).isEqualTo(PaymentReconciliationHandoffStatus.IN_PROGRESS);
        assertThat(result.createdBy()).isEqualTo("auditor.internal");
        assertThat(result.updatedBy()).isEqualTo("auditor.internal");
        assertThat(result.revisions()).hasSize(1);
        assertThat(result.revisions().getFirst().revisionNumber()).isEqualTo(1);
        assertThat(result.revisions().getFirst().reason()).isEqualTo("Dibuat setelah review register harian.");
        verify(auditTrailService).record(
                "auditor.internal",
                "PAYMENT",
                "CREATE_RECONCILIATION_HANDOFF_NOTE",
                result.id().toString(),
                "Dibuat setelah review register harian."
        );
    }

    @Test
    void revisesHandoffNoteWithNextRevisionAndPreservesHistory() {
        PaymentReconciliationSession session = completedSession("REC-20260801-REVISE");
        PaymentReconciliationHandoffNote note = new PaymentReconciliationHandoffNote(
                session.getId(),
                "Initial follow-up.",
                "finance.ops",
                LocalDate.parse("2026-08-03"),
                PaymentReconciliationHandoffStatus.OPEN,
                "auditor.internal"
        );
        PaymentReconciliationHandoffNoteRevision firstRevision = new PaymentReconciliationHandoffNoteRevision(
                note,
                1,
                "Initial note.",
                "auditor.internal"
        );
        AtomicReference<PaymentReconciliationHandoffNoteRevision> savedRevision = new AtomicReference<>();

        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(noteRepository.findBySessionIdAndId(session.getId(), note.getId())).thenReturn(Optional.of(note));
        when(noteRepository.save(any(PaymentReconciliationHandoffNote.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(revisionRepository.countByNoteId(note.getId())).thenReturn(1L);
        when(revisionRepository.save(any(PaymentReconciliationHandoffNoteRevision.class))).thenAnswer(invocation -> {
            PaymentReconciliationHandoffNoteRevision revision = invocation.getArgument(0);
            savedRevision.set(revision);
            return revision;
        });
        when(revisionRepository.findByNoteIdOrderByRevisionNumberAsc(note.getId()))
                .thenAnswer(invocation -> List.of(firstRevision, savedRevision.get()));

        PaymentReconciliationHandoffNoteEntry result = service.reviseNote(
                session.getId(),
                note.getId(),
                new PaymentReconciliationHandoffNoteCommand(
                        "Owner sudah konfirmasi bukti mutasi.",
                        "finance.supervisor",
                        LocalDate.parse("2026-08-04"),
                        PaymentReconciliationHandoffStatus.CLEARED,
                        "Bukti mutasi sudah dilampirkan pada register."
                ),
                "finance.supervisor"
        );

        assertThat(result.noteText()).isEqualTo("Owner sudah konfirmasi bukti mutasi.");
        assertThat(result.handoffOwner()).isEqualTo("finance.supervisor");
        assertThat(result.handoffStatus()).isEqualTo(PaymentReconciliationHandoffStatus.CLEARED);
        assertThat(result.updatedBy()).isEqualTo("finance.supervisor");
        assertThat(result.revisions()).extracting(PaymentReconciliationHandoffNoteRevisionEntry::revisionNumber)
                .containsExactly(1, 2);
        assertThat(result.revisions().getLast().reason()).isEqualTo("Bukti mutasi sudah dilampirkan pada register.");
        verify(auditTrailService).record(
                "finance.supervisor",
                "PAYMENT",
                "REVISE_RECONCILIATION_HANDOFF_NOTE",
                note.getId().toString(),
                "Bukti mutasi sudah dilampirkan pada register."
        );
    }

    @Test
    void readsHandoffNotesWithBatchRevisionHistory() {
        PaymentReconciliationSession session = completedSession("REC-20260801-LIST");
        PaymentReconciliationHandoffNote note = new PaymentReconciliationHandoffNote(
                session.getId(),
                "Follow up unresolved variance.",
                null,
                null,
                PaymentReconciliationHandoffStatus.OPEN,
                "auditor.internal"
        );
        PaymentReconciliationHandoffNoteRevision revision = new PaymentReconciliationHandoffNoteRevision(
                note,
                1,
                "Register review.",
                "auditor.internal"
        );
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(noteRepository.findBySessionIdOrderByUpdatedAtDescCreatedAtDesc(session.getId()))
                .thenReturn(List.of(note));
        when(revisionRepository.findByNoteIdInOrderByNoteIdAscRevisionNumberAsc(anyCollection()))
                .thenReturn(List.of(revision));

        List<PaymentReconciliationHandoffNoteEntry> result = service.handoffNotes(session.getId());

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(note.getId());
        assertThat(result.getFirst().revisions()).hasSize(1);
        assertThat(result.getFirst().revisions().getFirst().revisionNumber()).isEqualTo(1);
    }

    @Test
    void rejectsHandoffNoteForNonCompletedSession() {
        PaymentReconciliationSession session = openSession("REC-20260801-OPEN");
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.createNote(
                session.getId(),
                new PaymentReconciliationHandoffNoteCommand(
                        "Should be rejected.",
                        "finance.ops",
                        null,
                        PaymentReconciliationHandoffStatus.OPEN,
                        "Session belum completed."
                ),
                "auditor.internal"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("completed reconciliation sessions");
        verify(noteRepository, never()).save(any(PaymentReconciliationHandoffNote.class));
        verify(revisionRepository, never()).save(any(PaymentReconciliationHandoffNoteRevision.class));
    }

    @SuppressWarnings("unchecked")
    private static Collection<UUID> anyCollection() {
        return any(Collection.class);
    }

    private static PaymentReconciliationSession completedSession(String sessionNumber) {
        PaymentReconciliationSession session = openSession(sessionNumber);
        session.complete("Completed for handoff notes.");
        return session;
    }

    private static PaymentReconciliationSession openSession(String sessionNumber) {
        return new PaymentReconciliationSession(
                sessionNumber + "-" + UUID.randomUUID().toString().substring(0, 8),
                "bank-statement.csv",
                "BCA-OPERASIONAL",
                "finance.supervisor",
                new PaymentReconciliationMatchSummary(
                        1,
                        1,
                        0,
                        0,
                        0,
                        0,
                        0,
                        BigDecimal.ZERO.setScale(2)
                )
        );
    }
}
