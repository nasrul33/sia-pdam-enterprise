package id.pdam.sia.reporting.application;

import id.pdam.sia.payment.application.PaymentReconciliationMatchSummary;
import id.pdam.sia.payment.domain.PaymentReconciliationSession;
import id.pdam.sia.payment.repository.PaymentReconciliationSessionRepository;
import id.pdam.sia.reporting.domain.PaymentReconciliationHandoffNote;
import id.pdam.sia.reporting.domain.PaymentReconciliationHandoffStatus;
import id.pdam.sia.reporting.repository.PaymentReconciliationHandoffNoteRepository;
import id.pdam.sia.reporting.repository.PaymentReconciliationHandoffNoteRevisionCount;
import id.pdam.sia.reporting.repository.PaymentReconciliationHandoffNoteRevisionRepository;
import id.pdam.sia.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BankReconciliationHandoffWorkloadApplicationServiceTest {
    private final PaymentReconciliationHandoffNoteRepository noteRepository =
            mock(PaymentReconciliationHandoffNoteRepository.class);
    private final PaymentReconciliationHandoffNoteRevisionRepository revisionRepository =
            mock(PaymentReconciliationHandoffNoteRevisionRepository.class);
    private final PaymentReconciliationSessionRepository sessionRepository = mock(PaymentReconciliationSessionRepository.class);
    private final BankReconciliationHandoffWorkloadApplicationService service =
            new BankReconciliationHandoffWorkloadApplicationService(noteRepository, revisionRepository, sessionRepository);

    @Test
    void workloadListsFilteredNotesWithSessionTraceRevisionCountAndOverdueDays() {
        PaymentReconciliationSession session = completedSession("REC-20260801-SLA");
        PaymentReconciliationHandoffNote note = new PaymentReconciliationHandoffNote(
                session.getId(),
                "Reviewer meminta bukti settlement gateway.",
                "finance.ops",
                LocalDate.now(ZoneOffset.UTC).minusDays(2),
                PaymentReconciliationHandoffStatus.IN_PROGRESS,
                "auditor.internal"
        );
        when(noteRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<PaymentReconciliationHandoffNote>>any(),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(
                List.of(note),
                PageRequest.of(0, 10),
                1
        ));
        when(sessionRepository.findAllById(anyCollection())).thenReturn(List.of(session));
        when(revisionRepository.countRevisionsByNoteIdIn(anyCollection()))
                .thenReturn(List.of(count(note.getId(), 3)));

        Page<PaymentReconciliationHandoffWorkloadEntry> result = service.workload(
                new PaymentReconciliationHandoffWorkloadFilters(
                        PaymentReconciliationHandoffStatus.IN_PROGRESS,
                        " Finance ",
                        LocalDate.now(ZoneOffset.UTC).minusDays(7),
                        LocalDate.now(ZoneOffset.UTC).plusDays(1)
                ),
                0,
                10
        );

        assertThat(result.getTotalElements()).isEqualTo(1);
        PaymentReconciliationHandoffWorkloadEntry entry = result.getContent().getFirst();
        assertThat(entry.noteId()).isEqualTo(note.getId());
        assertThat(entry.sessionNumber()).isEqualTo(session.getSessionNumber());
        assertThat(entry.reviewStatus()).isEqualTo(PaymentReconciliationReviewStatus.PENDING_SIGN_OFF);
        assertThat(entry.handoffOwner()).isEqualTo("finance.ops");
        assertThat(entry.revisionCount()).isEqualTo(3);
        assertThat(entry.overdueDays()).isEqualTo(2);
    }

    @Test
    void workloadCsvUsesBoundedExportAndEscapesReviewerNotes() {
        PaymentReconciliationSession session = completedSession("REC-20260801-CSV");
        session.signOff("Approved after evidence review.", "finance.manager", "finance.supervisor");
        PaymentReconciliationHandoffNote note = new PaymentReconciliationHandoffNote(
                session.getId(),
                "Reviewer note, with comma",
                "finance.ops",
                LocalDate.parse("2026-08-03"),
                PaymentReconciliationHandoffStatus.CLEARED,
                "auditor.internal"
        );
        when(noteRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<PaymentReconciliationHandoffNote>>any(),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(
                List.of(note),
                PageRequest.of(0, 10_000),
                1
        ));
        when(sessionRepository.findAllById(anyCollection())).thenReturn(List.of(session));
        when(revisionRepository.countRevisionsByNoteIdIn(anyCollection()))
                .thenReturn(List.of(count(note.getId(), 1)));

        String csv = service.workloadCsv(new PaymentReconciliationHandoffWorkloadFilters(
                PaymentReconciliationHandoffStatus.CLEARED,
                "finance.ops",
                null,
                null
        ));

        assertThat(csv)
                .startsWith("note_id,session_id,session_number,bank_account_reference")
                .contains(session.getSessionNumber())
                .contains("SIGNED_OFF")
                .contains("CLEARED")
                .contains("finance.ops")
                .contains("2026-08-03")
                .contains("\"Reviewer note, with comma\"");
    }

    @Test
    void rejectsInvalidDueDateRangeBeforeQuery() {
        assertThatThrownBy(() -> service.workload(
                new PaymentReconciliationHandoffWorkloadFilters(
                        null,
                        null,
                        LocalDate.parse("2026-08-10"),
                        LocalDate.parse("2026-08-01")
                ),
                0,
                10
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("due date range is invalid");
        verify(noteRepository, never()).findAll(
                org.mockito.ArgumentMatchers.<Specification<PaymentReconciliationHandoffNote>>any(),
                any(Pageable.class)
        );
    }

    @Test
    void doesNotLoadSessionsOrRevisionCountsWhenWorkloadPageIsEmpty() {
        when(noteRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<PaymentReconciliationHandoffNote>>any(),
                any(Pageable.class)
        )).thenReturn(Page.empty(PageRequest.of(0, 10)));

        Page<PaymentReconciliationHandoffWorkloadEntry> result = service.workload(
                new PaymentReconciliationHandoffWorkloadFilters(null, null, null, null),
                0,
                10
        );

        assertThat(result.getContent()).isEmpty();
        verify(sessionRepository, never()).findAllById(anyCollection());
        verify(revisionRepository, never()).countRevisionsByNoteIdIn(anyCollection());
    }

    @SuppressWarnings("unchecked")
    private static Collection<UUID> anyCollection() {
        return any(Collection.class);
    }

    private static PaymentReconciliationHandoffNoteRevisionCount count(UUID noteId, long revisionCount) {
        return new PaymentReconciliationHandoffNoteRevisionCount() {
            @Override
            public UUID getNoteId() {
                return noteId;
            }

            @Override
            public long getRevisionCount() {
                return revisionCount;
            }
        };
    }

    private static PaymentReconciliationSession completedSession(String sessionNumber) {
        PaymentReconciliationSession session = new PaymentReconciliationSession(
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
        session.complete("Completed for handoff workload.");
        return session;
    }
}
