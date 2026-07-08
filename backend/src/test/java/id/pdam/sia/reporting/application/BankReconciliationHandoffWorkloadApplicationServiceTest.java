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
                        false,
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
                false,
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
                        false,
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
                new PaymentReconciliationHandoffWorkloadFilters(null, null, false, null, null),
                0,
                10
        );

        assertThat(result.getContent()).isEmpty();
        verify(sessionRepository, never()).findAllById(anyCollection());
        verify(revisionRepository, never()).countRevisionsByNoteIdIn(anyCollection());
    }

    @Test
    void ownerSlaGroupsStatusCountsPrioritizesOverdueOwnersAndExportsEscalationRows() {
        PaymentReconciliationSession session = completedSession("REC-20260801-OWNER");
        PaymentReconciliationHandoffNote criticalOpen = new PaymentReconciliationHandoffNote(
                session.getId(),
                "Critical owner note",
                "finance.ops",
                LocalDate.now(ZoneOffset.UTC).minusDays(8),
                PaymentReconciliationHandoffStatus.OPEN,
                "auditor.internal"
        );
        PaymentReconciliationHandoffNote activeProgress = new PaymentReconciliationHandoffNote(
                session.getId(),
                "Progress owner note",
                "finance.ops",
                LocalDate.now(ZoneOffset.UTC).plusDays(1),
                PaymentReconciliationHandoffStatus.IN_PROGRESS,
                "auditor.internal"
        );
        PaymentReconciliationHandoffNote unassignedOpen = new PaymentReconciliationHandoffNote(
                session.getId(),
                "Unassigned note",
                null,
                LocalDate.now(ZoneOffset.UTC).minusDays(2),
                PaymentReconciliationHandoffStatus.OPEN,
                "auditor.internal"
        );
        PaymentReconciliationHandoffNote cleared = new PaymentReconciliationHandoffNote(
                session.getId(),
                "Cleared note",
                "auditor.internal",
                LocalDate.now(ZoneOffset.UTC).minusDays(30),
                PaymentReconciliationHandoffStatus.CLEARED,
                "auditor.internal"
        );
        when(noteRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<PaymentReconciliationHandoffNote>>any(),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(
                List.of(criticalOpen, activeProgress, unassignedOpen, cleared),
                PageRequest.of(0, 10_000),
                4
        ));

        PaymentReconciliationHandoffOwnerSlaReport report = service.ownerSla(
                new PaymentReconciliationHandoffWorkloadFilters(null, null, false, null, null)
        );
        String csv = service.ownerSlaCsv(new PaymentReconciliationHandoffWorkloadFilters(null, null, false, null, null));

        assertThat(report.totalNotes()).isEqualTo(4);
        assertThat(report.openNotes()).isEqualTo(2);
        assertThat(report.inProgressNotes()).isEqualTo(1);
        assertThat(report.clearedNotes()).isEqualTo(1);
        assertThat(report.overdueNotes()).isEqualTo(2);
        assertThat(report.owners())
                .extracting(PaymentReconciliationHandoffOwnerSlaEntry::ownerLabel)
                .containsExactly("finance.ops", "UNASSIGNED", "auditor.internal");
        PaymentReconciliationHandoffOwnerSlaEntry financeOps = report.owners().getFirst();
        assertThat(financeOps.totalNotes()).isEqualTo(2);
        assertThat(financeOps.openNotes()).isEqualTo(1);
        assertThat(financeOps.inProgressNotes()).isEqualTo(1);
        assertThat(financeOps.maxOverdueDays()).isEqualTo(8);
        assertThat(financeOps.escalationPriority()).isEqualTo("CRITICAL");
        assertThat(report.owners().get(1).unassigned()).isTrue();
        assertThat(csv)
                .startsWith("owner,unassigned,total_notes,open_notes")
                .contains("finance.ops,false,2,1,1,0,1")
                .contains("UNASSIGNED,true,1,1,0,0,1")
                .contains("CRITICAL");
    }

    @Test
    void agingBucketsGroupActiveHandoffNotesByOwnerAndExportOnlyStaleQueues() {
        PaymentReconciliationSession session = completedSession("REC-20260801-BUCKET");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        PaymentReconciliationHandoffNote dueToday = new PaymentReconciliationHandoffNote(
                session.getId(),
                "Due today note",
                "finance.ops",
                today,
                PaymentReconciliationHandoffStatus.OPEN,
                "auditor.internal"
        );
        PaymentReconciliationHandoffNote overdueTwoDays = new PaymentReconciliationHandoffNote(
                session.getId(),
                "Overdue 1-3 note",
                "finance.ops",
                today.minusDays(2),
                PaymentReconciliationHandoffStatus.IN_PROGRESS,
                "auditor.internal"
        );
        PaymentReconciliationHandoffNote overdueFiveDays = new PaymentReconciliationHandoffNote(
                session.getId(),
                "Overdue 4-7 note",
                "finance.ops",
                today.minusDays(5),
                PaymentReconciliationHandoffStatus.OPEN,
                "auditor.internal"
        );
        PaymentReconciliationHandoffNote overdueNineDays = new PaymentReconciliationHandoffNote(
                session.getId(),
                "Overdue over 7 note",
                "finance.ops",
                today.minusDays(9),
                PaymentReconciliationHandoffStatus.OPEN,
                "auditor.internal"
        );
        PaymentReconciliationHandoffNote unassignedFuture = new PaymentReconciliationHandoffNote(
                session.getId(),
                "Future unassigned note",
                null,
                today.plusDays(3),
                PaymentReconciliationHandoffStatus.IN_PROGRESS,
                "auditor.internal"
        );
        PaymentReconciliationHandoffNote unassignedNoDueDate = new PaymentReconciliationHandoffNote(
                session.getId(),
                "No due date unassigned note",
                null,
                null,
                PaymentReconciliationHandoffStatus.OPEN,
                "auditor.internal"
        );
        when(noteRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<PaymentReconciliationHandoffNote>>any(),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(
                List.of(dueToday, overdueTwoDays, overdueFiveDays, overdueNineDays, unassignedFuture, unassignedNoDueDate),
                PageRequest.of(0, 10_000),
                6
        ));

        PaymentReconciliationHandoffAgingBucketReport report = service.agingBuckets(
                new PaymentReconciliationHandoffWorkloadFilters(null, null, false, null, null)
        );
        String csv = service.agingBucketsCsv(new PaymentReconciliationHandoffWorkloadFilters(null, null, false, null, null));

        assertThat(report.activeNotes()).isEqualTo(6);
        assertThat(report.dueTodayNotes()).isEqualTo(1);
        assertThat(report.overdue1To3Notes()).isEqualTo(1);
        assertThat(report.overdue4To7Notes()).isEqualTo(1);
        assertThat(report.overdueOver7Notes()).isEqualTo(1);
        assertThat(report.futureDueNotes()).isEqualTo(1);
        assertThat(report.noDueDateNotes()).isEqualTo(1);
        assertThat(report.staleNotes()).isEqualTo(3);
        assertThat(report.owners())
                .extracting(PaymentReconciliationHandoffAgingBucketEntry::ownerLabel)
                .containsExactly("finance.ops", "UNASSIGNED");
        PaymentReconciliationHandoffAgingBucketEntry financeOps = report.owners().getFirst();
        assertThat(financeOps.activeNotes()).isEqualTo(4);
        assertThat(financeOps.dueTodayNotes()).isEqualTo(1);
        assertThat(financeOps.overdue1To3Notes()).isEqualTo(1);
        assertThat(financeOps.overdue4To7Notes()).isEqualTo(1);
        assertThat(financeOps.overdueOver7Notes()).isEqualTo(1);
        assertThat(financeOps.staleNotes()).isEqualTo(3);
        assertThat(financeOps.maxOverdueDays()).isEqualTo(9);
        assertThat(csv)
                .startsWith("owner,unassigned,active_notes,due_today_notes")
                .contains("finance.ops,false,4,1,1,1,1")
                .doesNotContain("UNASSIGNED,true");
    }

    @Test
    void rejectsOwnerFilterWhenUnassignedScopeIsSelected() {
        assertThatThrownBy(() -> service.ownerSla(
                new PaymentReconciliationHandoffWorkloadFilters(
                        null,
                        "finance.ops",
                        true,
                        null,
                        null
                )
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("owner filter conflicts");
        verify(noteRepository, never()).findAll(
                org.mockito.ArgumentMatchers.<Specification<PaymentReconciliationHandoffNote>>any(),
                any(Pageable.class)
        );
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
