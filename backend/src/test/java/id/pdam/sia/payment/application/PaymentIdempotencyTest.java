package id.pdam.sia.payment.application;

import id.pdam.sia.accounting.application.AccountingApplicationService;
import id.pdam.sia.accounting.application.PaymentReversalPostingCommand;
import id.pdam.sia.accounting.application.PaymentSettlementPostingCommand;
import id.pdam.sia.accounting.domain.JournalEntry;
import id.pdam.sia.billing.domain.Invoice;
import id.pdam.sia.billing.domain.InvoiceStatus;
import id.pdam.sia.billing.repository.InvoiceRepository;
import id.pdam.sia.payment.domain.Payment;
import id.pdam.sia.payment.domain.PaymentAllocation;
import id.pdam.sia.payment.domain.PaymentReceipt;
import id.pdam.sia.payment.domain.PaymentStatus;
import id.pdam.sia.payment.repository.PaymentAllocationRepository;
import id.pdam.sia.payment.repository.PaymentReceiptRepository;
import id.pdam.sia.payment.repository.PaymentRepository;
import id.pdam.sia.payment.web.PaymentAllocationRequest;
import id.pdam.sia.payment.web.ReversePaymentRequest;
import id.pdam.sia.payment.web.SettleCounterPaymentRequest;
import id.pdam.sia.shared.audit.AuditTrailService;
import id.pdam.sia.shared.idempotency.IdempotencyRecord;
import id.pdam.sia.shared.idempotency.IdempotencyService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentIdempotencyTest {
    private static final Instant PAID_AT = Instant.parse("2026-07-31T12:00:00Z");
    private static final UUID CASH_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000001110");
    private static final UUID RECEIVABLE_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000001130");

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final PaymentAllocationRepository paymentAllocationRepository = mock(PaymentAllocationRepository.class);
    private final PaymentReceiptRepository paymentReceiptRepository = mock(PaymentReceiptRepository.class);
    private final InvoiceRepository invoiceRepository = mock(InvoiceRepository.class);
    private final IdempotencyService idempotencyService = mock(IdempotencyService.class);
    private final AuditTrailService auditTrailService = mock(AuditTrailService.class);
    private final AccountingApplicationService accountingApplicationService = mock(AccountingApplicationService.class);
    private final PaymentSettlementApplicationService service = new PaymentSettlementApplicationService(
            paymentRepository,
            paymentAllocationRepository,
            paymentReceiptRepository,
            invoiceRepository,
            idempotencyService,
            auditTrailService,
            accountingApplicationService
    );

    @Test
    void settlesCounterPaymentOnceWithIdempotencyReceiptAllocationAndAuditTrail() {
        Invoice invoice = issuedInvoice("INV-202607-SR-0001", new BigDecimal("100000.00"));
        IdempotencyRecord idempotencyRecord = IdempotencyRecord.reserve(
                "pay-counter-0001",
                "PAYMENT_SETTLEMENT",
                "sha256:payment",
                Instant.now().plusSeconds(3600)
        );

        when(idempotencyService.reserve(eq("pay-counter-0001"), eq("PAYMENT_SETTLEMENT"), any(String.class), any(Instant.class)))
                .thenReturn(idempotencyRecord);
        when(paymentRepository.findByExternalReference("COUNTER-202607-0001")).thenReturn(Optional.empty());
        when(invoiceRepository.findAllById(List.of(invoice.getId()))).thenReturn(List.of(invoice));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentAllocationRepository.save(any(PaymentAllocation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentReceiptRepository.save(any(PaymentReceipt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        JournalEntry settlementJournal = JournalEntry.draft("PMT-PAY-20260731-0001", UUID.randomUUID(), "Payment settlement");
        when(accountingApplicationService.postPaymentSettlement(any(PaymentSettlementPostingCommand.class), eq("kasir.loket")))
                .thenReturn(settlementJournal);

        PaymentSettlementResult result = service.settleCounterPayment(
                request(invoice.getId(), "COUNTER-202607-0001", "bayar loket"),
                "pay-counter-0001",
                "kasir.loket"
        );

        assertThat(result.payment().getStatus()).isEqualTo(PaymentStatus.SETTLED);
        assertThat(result.payment().getChannel()).isEqualTo("COUNTER");
        assertThat(result.payment().getAmount()).isEqualByComparingTo("100000.00");
        assertThat(result.payment().getSettlementJournalEntryId()).isEqualTo(settlementJournal.getId());
        assertThat(result.receipt().getPaymentId()).isEqualTo(result.payment().getId());
        assertThat(result.receipt().getReceiptNumber()).startsWith("RCT-");
        assertThat(result.allocations()).hasSize(1);
        assertThat(invoice.getPaidAmount()).isEqualByComparingTo("100000.00");
        assertThat(invoice.getOutstandingAmount()).isEqualByComparingTo("0.00");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(idempotencyRecord.isCompleted()).isTrue();
        assertThat(idempotencyRecord.getResponseReference()).isEqualTo(result.payment().getId().toString());

        ArgumentCaptor<PaymentAllocation> allocationCaptor = ArgumentCaptor.forClass(PaymentAllocation.class);
        verify(paymentAllocationRepository).save(allocationCaptor.capture());
        assertThat(allocationCaptor.getValue().getPaymentId()).isEqualTo(result.payment().getId());
        assertThat(allocationCaptor.getValue().getInvoiceId()).isEqualTo(invoice.getId());
        assertThat(allocationCaptor.getValue().getAmount()).isEqualByComparingTo("100000.00");
        ArgumentCaptor<PaymentSettlementPostingCommand> postingCaptor = ArgumentCaptor.forClass(PaymentSettlementPostingCommand.class);
        verify(accountingApplicationService).postPaymentSettlement(postingCaptor.capture(), eq("kasir.loket"));
        assertThat(postingCaptor.getValue().paymentNumber()).isEqualTo(result.payment().getPaymentNumber());
        assertThat(postingCaptor.getValue().paymentId()).isEqualTo(result.payment().getId());
        assertThat(postingCaptor.getValue().period()).isEqualTo("2026-07");
        assertThat(postingCaptor.getValue().amount()).isEqualByComparingTo("100000.00");
        assertThat(postingCaptor.getValue().cashAccountId()).isEqualTo(CASH_ACCOUNT_ID);
        assertThat(postingCaptor.getValue().receivableAccountId()).isEqualTo(RECEIVABLE_ACCOUNT_ID);
        verify(auditTrailService).record(
                "kasir.loket",
                "PAYMENT",
                "SETTLE_COUNTER_PAYMENT",
                result.payment().getId().toString(),
                "bayar loket"
        );
    }

    @Test
    void returnsExistingPaymentWhenIdempotencyKeyAlreadyCompleted() {
        Payment existingPayment = new Payment(
                "PAY-20260731-EXISTING",
                "pay-counter-0001",
                "COUNTER",
                "COUNTER-202607-0001",
                new BigDecimal("100000.00"),
                PAID_AT
        );
        PaymentReceipt existingReceipt = new PaymentReceipt(existingPayment.getId(), "RCT-20260731-EXISTING", PAID_AT);
        PaymentAllocation existingAllocation = new PaymentAllocation(existingPayment.getId(), UUID.randomUUID(), new BigDecimal("100000.00"));
        IdempotencyRecord idempotencyRecord = IdempotencyRecord.reserve(
                "pay-counter-0001",
                "PAYMENT_SETTLEMENT",
                "sha256:payment",
                Instant.now().plusSeconds(3600)
        );
        idempotencyRecord.markCompleted(existingPayment.getId().toString());

        when(idempotencyService.reserve(eq("pay-counter-0001"), eq("PAYMENT_SETTLEMENT"), any(String.class), any(Instant.class)))
                .thenReturn(idempotencyRecord);
        when(paymentRepository.findById(existingPayment.getId())).thenReturn(Optional.of(existingPayment));
        when(paymentReceiptRepository.findByPaymentId(existingPayment.getId())).thenReturn(Optional.of(existingReceipt));
        when(paymentAllocationRepository.findByPaymentId(existingPayment.getId())).thenReturn(List.of(existingAllocation));

        PaymentSettlementResult result = service.settleCounterPayment(
                request(UUID.randomUUID(), "COUNTER-202607-0001", "retry"),
                "pay-counter-0001",
                "kasir.loket"
        );

        assertThat(result.payment()).isSameAs(existingPayment);
        assertThat(result.receipt()).isSameAs(existingReceipt);
        assertThat(result.allocations()).containsExactly(existingAllocation);
        verify(invoiceRepository, never()).findAllById(any());
        verify(paymentRepository, never()).save(any());
        verify(paymentAllocationRepository, never()).save(any());
        verify(paymentReceiptRepository, never()).save(any());
        verify(accountingApplicationService, never()).postPaymentSettlement(any(), any());
        verify(auditTrailService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void reversesSettledCounterPaymentWithInvoiceRestorationAndAccountingJournal() {
        Invoice invoice = issuedInvoice("INV-202607-SR-0001", new BigDecimal("100000.00"));
        invoice.applyPayment(new BigDecimal("100000.00"));
        Payment payment = new Payment(
                "PAY-20260731-0001",
                "pay-counter-0001",
                "COUNTER",
                "COUNTER-202607-0001",
                new BigDecimal("100000.00"),
                PAID_AT
        );
        payment.linkSettlementJournal(UUID.randomUUID());
        PaymentAllocation allocation = new PaymentAllocation(payment.getId(), invoice.getId(), new BigDecimal("100000.00"));
        PaymentReceipt receipt = new PaymentReceipt(payment.getId(), "RCT-20260731-0001", PAID_AT);
        JournalEntry reversalJournal = JournalEntry.draft("REV-PAY-20260731-0001", UUID.randomUUID(), "Payment reversal");

        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentAllocationRepository.findByPaymentId(payment.getId())).thenReturn(List.of(allocation));
        when(invoiceRepository.findAllById(List.of(invoice.getId()))).thenReturn(List.of(invoice));
        when(paymentReceiptRepository.findByPaymentId(payment.getId())).thenReturn(Optional.of(receipt));
        when(accountingApplicationService.postPaymentReversal(any(PaymentReversalPostingCommand.class), eq("finance.supervisor")))
                .thenReturn(reversalJournal);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentSettlementResult result = service.reversePayment(
                payment.getId(),
                new ReversePaymentRequest(CASH_ACCOUNT_ID, RECEIVABLE_ACCOUNT_ID, "salah input pembayaran"),
                "finance.supervisor"
        );

        assertThat(result.payment().getStatus()).isEqualTo(PaymentStatus.REVERSED);
        assertThat(result.payment().getReversedAt()).isNotNull();
        assertThat(result.payment().getReversalReason()).isEqualTo("salah input pembayaran");
        assertThat(result.payment().getReversalJournalEntryId()).isEqualTo(reversalJournal.getId());
        assertThat(invoice.getPaidAmount()).isEqualByComparingTo("0.00");
        assertThat(invoice.getOutstandingAmount()).isEqualByComparingTo("100000.00");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.ISSUED);

        ArgumentCaptor<PaymentReversalPostingCommand> postingCaptor = ArgumentCaptor.forClass(PaymentReversalPostingCommand.class);
        verify(accountingApplicationService).postPaymentReversal(postingCaptor.capture(), eq("finance.supervisor"));
        assertThat(postingCaptor.getValue().paymentNumber()).isEqualTo("PAY-20260731-0001");
        assertThat(postingCaptor.getValue().paymentId()).isEqualTo(payment.getId());
        assertThat(postingCaptor.getValue().period()).isEqualTo("2026-07");
        assertThat(postingCaptor.getValue().amount()).isEqualByComparingTo("100000.00");
        assertThat(postingCaptor.getValue().cashAccountId()).isEqualTo(CASH_ACCOUNT_ID);
        assertThat(postingCaptor.getValue().receivableAccountId()).isEqualTo(RECEIVABLE_ACCOUNT_ID);
        verify(auditTrailService).record(
                "finance.supervisor",
                "PAYMENT",
                "REVERSE_PAYMENT",
                payment.getId().toString(),
                "salah input pembayaran"
        );
    }

    @Test
    void rejectsPaymentReversalWhenPaymentIsAlreadyReversedBeforeLoadingAllocations() {
        Payment payment = new Payment(
                "PAY-20260731-0001",
                "pay-counter-0001",
                "COUNTER",
                "COUNTER-202607-0001",
                new BigDecimal("100000.00"),
                PAID_AT
        );
        payment.linkSettlementJournal(UUID.randomUUID());
        payment.reverse(PAID_AT.plusSeconds(60), "sudah dibalik", UUID.randomUUID());

        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.reversePayment(
                payment.getId(),
                new ReversePaymentRequest(CASH_ACCOUNT_ID, RECEIVABLE_ACCOUNT_ID, "retry reversal"),
                "finance.supervisor"
        ))
                .isInstanceOf(id.pdam.sia.shared.exception.BusinessException.class)
                .hasMessageContaining("Only settled payment can be reversed");

        verify(paymentAllocationRepository, never()).findByPaymentId(any());
        verify(accountingApplicationService, never()).postPaymentReversal(any(), any());
        verify(paymentRepository, never()).save(any());
    }

    private static SettleCounterPaymentRequest request(UUID invoiceId, String externalReference, String reason) {
        return new SettleCounterPaymentRequest(
                externalReference,
                new BigDecimal("100000.00"),
                PAID_AT,
                List.of(new PaymentAllocationRequest(invoiceId, new BigDecimal("100000.00"))),
                CASH_ACCOUNT_ID,
                RECEIVABLE_ACCOUNT_ID,
                reason
        );
    }

    private static Invoice issuedInvoice(String invoiceNumber, BigDecimal amount) {
        Invoice invoice = new Invoice(
                UUID.randomUUID(),
                UUID.randomUUID(),
                invoiceNumber,
                "2026-07",
                amount,
                LocalDate.of(2026, 8, 20)
        );
        invoice.markIssued(PAID_AT);
        return invoice;
    }
}
