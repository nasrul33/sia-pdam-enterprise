package id.pdam.sia.payment.application;

import id.pdam.sia.payment.domain.Payment;
import id.pdam.sia.payment.domain.PaymentAllocation;
import id.pdam.sia.payment.domain.PaymentReceipt;
import id.pdam.sia.payment.domain.PaymentStatus;
import id.pdam.sia.payment.repository.PaymentAllocationRepository;
import id.pdam.sia.payment.repository.PaymentReceiptRepository;
import id.pdam.sia.payment.repository.PaymentRepository;
import id.pdam.sia.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentQueryApplicationServiceTest {
    private static final Instant PAID_AT = Instant.parse("2026-07-31T12:00:00Z");

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final PaymentReceiptRepository paymentReceiptRepository = mock(PaymentReceiptRepository.class);
    private final PaymentAllocationRepository paymentAllocationRepository = mock(PaymentAllocationRepository.class);
    private final PaymentQueryApplicationService service = new PaymentQueryApplicationService(
            paymentRepository,
            paymentReceiptRepository,
            paymentAllocationRepository
    );

    @Test
    void listPaymentsNormalizesChannelAndAppliesDescendingPaging() {
        Payment payment = payment("PAY-20260731-0001", PaymentStatus.SETTLED);
        when(paymentRepository.findByStatusAndChannel(eq(PaymentStatus.SETTLED), eq("COUNTER"), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(payment)));

        assertThat(service.listPayments(PaymentStatus.SETTLED, " counter ", null, 0, 500).getContent())
                .containsExactly(payment);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(paymentRepository).findByStatusAndChannel(eq(PaymentStatus.SETTLED), eq("COUNTER"), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("paidAt")).isNotNull();
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("paidAt").isDescending()).isTrue();
    }

    @Test
    void listPaymentsUsesUnfilteredRepositoryWhenStatusAndChannelAreEmpty() {
        when(paymentRepository.findAll(org.mockito.ArgumentMatchers.any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.listPayments(null, " ", null, -1, 0);

        verify(paymentRepository).findAll(org.mockito.ArgumentMatchers.any(Pageable.class));
        verify(paymentRepository, never()).findByStatus(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(paymentRepository, never()).findByChannel(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void listPaymentsUsesBoundedSearchWithExistingFilters() {
        Payment payment = payment("PAY-20260731-0001", PaymentStatus.SETTLED);
        when(paymentRepository.search(
                eq(PaymentStatus.SETTLED),
                eq("COUNTER"),
                eq("PAY-2026"),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(payment)));

        assertThat(service.listPayments(PaymentStatus.SETTLED, " counter ", " PAY-2026 ", 0, 20).getContent())
                .containsExactly(payment);

        verify(paymentRepository).search(
                eq(PaymentStatus.SETTLED),
                eq("COUNTER"),
                eq("PAY-2026"),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        );
    }

    @Test
    void getPaymentHydratesReceiptAllocationsAndRejectsMissingReceipt() {
        Payment payment = payment("PAY-20260731-0001", PaymentStatus.SETTLED);
        PaymentReceipt receipt = new PaymentReceipt(payment.getId(), "RCT-20260731-0001", PAID_AT);
        PaymentAllocation allocation = new PaymentAllocation(payment.getId(), UUID.randomUUID(), new BigDecimal("100000.00"));

        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentReceiptRepository.findByPaymentId(payment.getId())).thenReturn(Optional.of(receipt));
        when(paymentAllocationRepository.findByPaymentId(payment.getId())).thenReturn(List.of(allocation));

        PaymentSettlementResult result = service.getPayment(payment.getId());

        assertThat(result.payment()).isSameAs(payment);
        assertThat(result.receipt()).isSameAs(receipt);
        assertThat(result.allocations()).containsExactly(allocation);

        when(paymentReceiptRepository.findByPaymentId(payment.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPayment(payment.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Payment receipt was not found");
    }

    private static Payment payment(String paymentNumber, PaymentStatus expectedStatus) {
        Payment payment = new Payment(
                paymentNumber,
                paymentNumber.toLowerCase(),
                "COUNTER",
                paymentNumber,
                new BigDecimal("100000.00"),
                PAID_AT
        );
        assertThat(payment.getStatus()).isEqualTo(expectedStatus);
        return payment;
    }
}
