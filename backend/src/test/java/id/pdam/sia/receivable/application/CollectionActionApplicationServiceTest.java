package id.pdam.sia.receivable.application;

import id.pdam.sia.billing.domain.Invoice;
import id.pdam.sia.billing.repository.InvoiceRepository;
import id.pdam.sia.customer.domain.Customer;
import id.pdam.sia.customer.repository.CustomerRepository;
import id.pdam.sia.receivable.domain.CollectionAction;
import id.pdam.sia.receivable.domain.CollectionActionStatus;
import id.pdam.sia.receivable.domain.CollectionActionType;
import id.pdam.sia.receivable.repository.CollectionActionRepository;
import id.pdam.sia.receivable.web.CollectionActionWorkflowRequest;
import id.pdam.sia.receivable.web.CreateCollectionActionRequest;
import id.pdam.sia.shared.audit.AuditTrailService;
import id.pdam.sia.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectionActionApplicationServiceTest {
    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000010001");
    private static final UUID INVOICE_ID = UUID.fromString("00000000-0000-0000-0000-000000020001");
    private static final Instant SCHEDULED_AT = Instant.parse("2026-07-06T02:00:00Z");
    private static final Instant ISSUED_AT = Instant.parse("2026-06-01T00:00:00Z");

    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final InvoiceRepository invoiceRepository = mock(InvoiceRepository.class);
    private final CollectionActionRepository collectionActionRepository = mock(CollectionActionRepository.class);
    private final AuditTrailService auditTrailService = mock(AuditTrailService.class);
    private final CollectionActionApplicationService service = new CollectionActionApplicationService(
            customerRepository,
            invoiceRepository,
            collectionActionRepository,
            auditTrailService
    );

    @Test
    void createsDunningActionForOverdueOpenInvoiceWithAuditTrailAndDuplicateGuard() {
        Invoice invoice = issuedInvoice("INV-OVERDUE", new BigDecimal("125000.00"), LocalDate.of(2026, 6, 1));
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(activeCustomer()));
        when(invoiceRepository.findById(INVOICE_ID)).thenReturn(Optional.of(invoice));
        when(collectionActionRepository.existsByInvoiceIdAndActionTypeAndStatusIn(
                INVOICE_ID,
                CollectionActionType.WARNING_LETTER,
                CollectionActionStatus.activeStatuses()
        )).thenReturn(false);
        when(collectionActionRepository.save(any(CollectionAction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CollectionAction action = service.createAction(
                new CreateCollectionActionRequest(
                        CUSTOMER_ID,
                        INVOICE_ID,
                        CollectionActionType.WARNING_LETTER,
                        SCHEDULED_AT,
                        "Surat peringatan tunggakan rekening air",
                        "jadwalkan SP1"
                ),
                "piutang.admin"
        );

        assertThat(action.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(action.getInvoiceId()).isEqualTo(INVOICE_ID);
        assertThat(action.getActionType()).isEqualTo(CollectionActionType.WARNING_LETTER);
        assertThat(action.getStatus()).isEqualTo(CollectionActionStatus.OPEN);
        assertThat(action.getScheduledAt()).isEqualTo(SCHEDULED_AT);
        assertThat(action.getCompletedAt()).isNull();
        assertThat(action.getNotes()).isEqualTo("Surat peringatan tunggakan rekening air");
        verify(auditTrailService).record(
                "piutang.admin",
                "RECEIVABLE",
                "CREATE_COLLECTION_ACTION",
                action.getId().toString(),
                "jadwalkan SP1"
        );
    }

    @Test
    void rejectsDuplicateActiveDunningActionForSameInvoiceAndType() {
        Invoice invoice = issuedInvoice("INV-DUP", new BigDecimal("100000.00"), LocalDate.of(2026, 6, 1));
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(activeCustomer()));
        when(invoiceRepository.findById(INVOICE_ID)).thenReturn(Optional.of(invoice));
        when(collectionActionRepository.existsByInvoiceIdAndActionTypeAndStatusIn(
                INVOICE_ID,
                CollectionActionType.WARNING_LETTER,
                CollectionActionStatus.activeStatuses()
        )).thenReturn(true);

        assertThatThrownBy(() -> service.createAction(
                new CreateCollectionActionRequest(
                        CUSTOMER_ID,
                        INVOICE_ID,
                        CollectionActionType.WARNING_LETTER,
                        SCHEDULED_AT,
                        "duplikat",
                        "duplikat SP1"
                ),
                "piutang.admin"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("active collection action");
        verify(collectionActionRepository, never()).save(any());
        verify(auditTrailService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsDunningActionWhenInvoiceIsNotOverdueOnScheduleDate() {
        Invoice invoice = issuedInvoice("INV-CURRENT", new BigDecimal("100000.00"), LocalDate.of(2026, 7, 6));
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(activeCustomer()));
        when(invoiceRepository.findById(INVOICE_ID)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> service.createAction(
                new CreateCollectionActionRequest(
                        CUSTOMER_ID,
                        INVOICE_ID,
                        CollectionActionType.WARNING_LETTER,
                        SCHEDULED_AT,
                        "belum overdue",
                        "validasi jatuh tempo"
                ),
                "piutang.admin"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("overdue");
        verify(collectionActionRepository, never()).save(any());
    }

    @Test
    void rejectsCollectionActionWhenCustomerIsInactive() {
        Customer inactive = activeCustomer();
        inactive.deactivate();
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.createAction(
                new CreateCollectionActionRequest(
                        CUSTOMER_ID,
                        null,
                        CollectionActionType.FIELD_VISIT,
                        SCHEDULED_AT,
                        "kunjungan pelanggan",
                        "validasi pelanggan"
                ),
                "piutang.admin"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("active");
        verify(collectionActionRepository, never()).save(any());
    }

    @Test
    void startsAndCompletesCollectionActionWithAuditTrail() {
        CollectionAction action = new CollectionAction(
                CUSTOMER_ID,
                INVOICE_ID,
                CollectionActionType.FIELD_VISIT,
                SCHEDULED_AT,
                "kunjungan awal"
        );
        when(collectionActionRepository.findById(action.getId())).thenReturn(Optional.of(action));
        when(collectionActionRepository.save(any(CollectionAction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CollectionAction started = service.startAction(
                action.getId(),
                new CollectionActionWorkflowRequest("petugas berangkat", "mulai kunjungan"),
                "piutang.officer"
        );
        CollectionAction completed = service.completeAction(
                action.getId(),
                new CollectionActionWorkflowRequest("pelanggan janji bayar", "selesai kunjungan"),
                "piutang.officer"
        );

        assertThat(started).isSameAs(action);
        assertThat(completed.getStatus()).isEqualTo(CollectionActionStatus.COMPLETED);
        assertThat(completed.getCompletedAt()).isNotNull();
        assertThat(completed.getNotes()).isEqualTo("pelanggan janji bayar");
        verify(auditTrailService).record(
                "piutang.officer",
                "RECEIVABLE",
                "START_COLLECTION_ACTION",
                action.getId().toString(),
                "mulai kunjungan"
        );
        verify(auditTrailService).record(
                "piutang.officer",
                "RECEIVABLE",
                "COMPLETE_COLLECTION_ACTION",
                action.getId().toString(),
                "selesai kunjungan"
        );
    }

    @Test
    void rejectsCancelWhenCollectionActionAlreadyCompleted() {
        CollectionAction action = new CollectionAction(
                CUSTOMER_ID,
                INVOICE_ID,
                CollectionActionType.FIELD_VISIT,
                SCHEDULED_AT,
                "kunjungan"
        );
        action.start(Instant.parse("2026-07-06T03:00:00Z"));
        action.complete(Instant.parse("2026-07-06T04:00:00Z"), "selesai");
        when(collectionActionRepository.findById(action.getId())).thenReturn(Optional.of(action));

        assertThatThrownBy(() -> service.cancelAction(
                action.getId(),
                new CollectionActionWorkflowRequest("tidak boleh", "cancel selesai"),
                "piutang.spv"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("open or in-progress");
        verify(collectionActionRepository, never()).save(any());
    }

    private static Invoice issuedInvoice(String invoiceNumber, BigDecimal amount, LocalDate dueDate) {
        Invoice invoice = new Invoice(
                UUID.randomUUID(),
                UUID.randomUUID(),
                invoiceNumber,
                "2026-06",
                amount,
                dueDate
        );
        invoice.markIssued(ISSUED_AT);
        return invoice;
    }

    private static Customer activeCustomer() {
        return new Customer("C-0001", "Budi Santoso", null, null);
    }
}
