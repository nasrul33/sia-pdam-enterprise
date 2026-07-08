package id.pdam.sia.reporting.application;

import id.pdam.sia.payment.application.BankStatementRowCommand;
import id.pdam.sia.payment.application.PaymentReconciliationMatchResult;
import id.pdam.sia.payment.application.PaymentReconciliationMatchSummary;
import id.pdam.sia.payment.domain.PaymentReconciliationItem;
import id.pdam.sia.payment.domain.PaymentReconciliationResolutionStatus;
import id.pdam.sia.payment.domain.PaymentReconciliationSession;
import id.pdam.sia.payment.repository.PaymentReconciliationItemRepository;
import id.pdam.sia.payment.repository.PaymentReconciliationSessionRepository;
import id.pdam.sia.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

class BankReconciliationReviewRegisterApplicationServiceTest {
    private static final Instant TRANSACTED_AT = Instant.parse("2026-07-31T12:00:00Z");

    private final PaymentReconciliationSessionRepository sessionRepository = mock(PaymentReconciliationSessionRepository.class);
    private final PaymentReconciliationItemRepository itemRepository = mock(PaymentReconciliationItemRepository.class);
    private final BankReconciliationReviewRegisterApplicationService service =
            new BankReconciliationReviewRegisterApplicationService(sessionRepository, itemRepository);

    @Test
    void reviewRegisterSummarizesCompletedSessionsAndSignedOffState() {
        PaymentReconciliationSession pendingSession = completedSession("REC-20260731-PENDING");
        PaymentReconciliationSession signedSession = completedSession("REC-20260731-SIGNED");
        signedSession.signOff("Approved month-end evidence.", "finance.manager", "finance.supervisor");
        PaymentReconciliationItem exceptionItem = PaymentReconciliationItem.from(
                pendingSession.getId(),
                PaymentReconciliationMatchResult.unmatched(1, new BankStatementRowCommand(
                        "BANK-FEE",
                        new BigDecimal("2500.00"),
                        TRANSACTED_AT,
                        "bank"
                ))
        );
        exceptionItem.resolve(PaymentReconciliationResolutionStatus.ACCEPTED, "Biaya admin bank.", "auditor.internal");
        exceptionItem.linkAdjustmentJournal(UUID.randomUUID(), "Posting biaya admin.", "finance.supervisor");
        PaymentReconciliationItem signedItem = PaymentReconciliationItem.from(
                signedSession.getId(),
                PaymentReconciliationMatchResult.unmatched(1, new BankStatementRowCommand(
                        "BANK-ROUNDING",
                        new BigDecimal("1000.00"),
                        TRANSACTED_AT,
                        "bank"
                ))
        );
        signedItem.resolve(PaymentReconciliationResolutionStatus.IGNORED, "Rounding bank diterima.", "auditor.internal");
        when(sessionRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<PaymentReconciliationSession>>any(),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(
                List.of(pendingSession, signedSession),
                PageRequest.of(0, 10),
                2
        ));
        when(itemRepository.findBySessionIdInOrderBySessionIdAscRowNumberAsc(anyCollection()))
                .thenReturn(List.of(exceptionItem, signedItem));

        Page<BankReconciliationReviewRegisterEntry> result = service.reviewRegister(
                new BankReconciliationReviewRegisterFilters(null, null, null),
                0,
                10
        );

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().getFirst().reviewStatus()).isEqualTo(PaymentReconciliationReviewStatus.PENDING_SIGN_OFF);
        assertThat(result.getContent().getFirst().exceptionItems()).isEqualTo(1);
        assertThat(result.getContent().getFirst().adjustedItems()).isEqualTo(1);
        assertThat(result.getContent().getFirst().pendingSignOffAgeDays()).isGreaterThanOrEqualTo(0);
        assertThat(result.getContent().getLast().reviewStatus()).isEqualTo(PaymentReconciliationReviewStatus.SIGNED_OFF);
        assertThat(result.getContent().getLast().signedOffBy()).isEqualTo("finance.manager");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(sessionRepository).findAll(
                org.mockito.ArgumentMatchers.<Specification<PaymentReconciliationSession>>any(),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("completedAt")).isNotNull();
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("completedAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void entryCalculatesPendingSignOffAgeDaysFromCompletionDate() {
        PaymentReconciliationSession session = completedSession("REC-20260731-AGE");
        Instant generatedAt = session.getCompletedAt().plus(3, ChronoUnit.DAYS).plusSeconds(60);

        BankReconciliationReviewRegisterEntry entry = BankReconciliationReviewRegisterEntry.from(
                session,
                List.of(),
                generatedAt
        );

        assertThat(entry.pendingSignOffAgeDays()).isEqualTo(3);
        assertThat(entry.reviewStatus()).isEqualTo(PaymentReconciliationReviewStatus.PENDING_SIGN_OFF);
    }

    @Test
    void reviewRegisterCsvContainsHandoffColumnsAndUsesBoundedExportPage() {
        PaymentReconciliationSession session = completedSession("REC-20260731-CSV");
        PaymentReconciliationItem item = PaymentReconciliationItem.from(
                session.getId(),
                PaymentReconciliationMatchResult.unmatched(1, new BankStatementRowCommand(
                        "BANK-FEE-CSV",
                        new BigDecimal("2500.00"),
                        TRANSACTED_AT,
                        "bank"
                ))
        );
        item.resolve(PaymentReconciliationResolutionStatus.ACCEPTED, "Biaya admin bank.", "auditor.internal");
        when(sessionRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<PaymentReconciliationSession>>any(),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(
                List.of(session),
                PageRequest.of(0, 10_000),
                1
        ));
        when(itemRepository.findBySessionIdInOrderBySessionIdAscRowNumberAsc(anyCollection()))
                .thenReturn(List.of(item));

        String csv = service.reviewRegisterCsv(new BankReconciliationReviewRegisterFilters(
                PaymentReconciliationReviewStatus.PENDING_SIGN_OFF,
                null,
                null
        ));

        assertThat(csv)
                .startsWith("session_id,session_number,review_status,bank_account_reference")
                .contains("pending_sign_off_age_days,generated_at,reviewer_notes,handoff_owner,handoff_due_date,handoff_status")
                .contains(session.getSessionNumber())
                .contains("PENDING_SIGN_OFF")
                .contains("BCA-OPERASIONAL")
                .contains("0.00");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(sessionRepository).findAll(
                org.mockito.ArgumentMatchers.<Specification<PaymentReconciliationSession>>any(),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10_000);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("completedAt")).isNotNull();
    }

    @Test
    void rejectsInvalidCompletedDateRange() {
        assertThatThrownBy(() -> service.reviewRegister(
                new BankReconciliationReviewRegisterFilters(
                        PaymentReconciliationReviewStatus.PENDING_SIGN_OFF,
                        Instant.parse("2026-08-01T00:00:00Z"),
                        Instant.parse("2026-07-31T00:00:00Z")
                ),
                0,
                10
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("date range is invalid");
    }

    @Test
    void doesNotQueryItemsWhenPageIsEmpty() {
        when(sessionRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<PaymentReconciliationSession>>any(),
                any(Pageable.class)
        )).thenReturn(Page.empty(PageRequest.of(0, 10)));

        Page<BankReconciliationReviewRegisterEntry> result = service.reviewRegister(
                new BankReconciliationReviewRegisterFilters(null, null, null),
                0,
                10
        );

        assertThat(result.getContent()).isEmpty();
        verify(itemRepository, never()).findBySessionIdInOrderBySessionIdAscRowNumberAsc(anyCollection());
    }

    @SuppressWarnings("unchecked")
    private static Collection<UUID> anyCollection() {
        return any(Collection.class);
    }

    private static PaymentReconciliationSession completedSession(String sessionNumber) {
        PaymentReconciliationSession session = new PaymentReconciliationSession(
                sessionNumber + "-" + UUID.randomUUID().toString().substring(0, 8),
                "bank-statement.csv",
                "BCA-OPERASIONAL",
                "finance.supervisor",
                new PaymentReconciliationMatchSummary(
                        1,
                        0,
                        0,
                        0,
                        0,
                        0,
                        1,
                        BigDecimal.ZERO.setScale(2)
                )
        );
        session.complete("Completed for review register.");
        return session;
    }
}
