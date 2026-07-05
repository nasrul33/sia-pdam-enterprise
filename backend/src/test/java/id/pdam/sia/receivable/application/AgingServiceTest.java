package id.pdam.sia.receivable.application;

import id.pdam.sia.billing.domain.Invoice;
import id.pdam.sia.billing.repository.InvoiceRepository;
import id.pdam.sia.receivable.domain.ReceivableAgingSnapshot;
import id.pdam.sia.receivable.repository.ReceivableAgingSnapshotRepository;
import id.pdam.sia.receivable.web.GenerateReceivableAgingSnapshotRequest;
import id.pdam.sia.shared.audit.AuditTrailService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgingServiceTest {
    private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 7, 31);
    private static final Instant ISSUED_AT = Instant.parse("2026-07-01T00:00:00Z");

    private final InvoiceRepository invoiceRepository = mock(InvoiceRepository.class);
    private final ReceivableAgingSnapshotRepository snapshotRepository = mock(ReceivableAgingSnapshotRepository.class);
    private final AuditTrailService auditTrailService = mock(AuditTrailService.class);
    private final ReceivableAgingApplicationService service = new ReceivableAgingApplicationService(
            invoiceRepository,
            snapshotRepository,
            auditTrailService
    );

    @Test
    void generatesAgingSnapshotWithValidBucketsAndAuditTrail() {
        Invoice current = issuedInvoice("INV-CURRENT", new BigDecimal("100000.00"), AS_OF_DATE);
        Invoice bucket30 = issuedInvoice("INV-030", new BigDecimal("200000.00"), AS_OF_DATE.minusDays(30));
        Invoice bucket60 = issuedInvoice("INV-060", new BigDecimal("300000.00"), AS_OF_DATE.minusDays(31));
        Invoice bucket90 = issuedInvoice("INV-090", new BigDecimal("400000.00"), AS_OF_DATE.minusDays(90));
        Invoice over90 = issuedInvoice("INV-091", new BigDecimal("500000.00"), AS_OF_DATE.minusDays(91));
        Invoice paid = issuedInvoice("INV-PAID", new BigDecimal("900000.00"), AS_OF_DATE.minusDays(120));
        paid.applyPayment(new BigDecimal("900000.00"));
        Invoice draft = draftInvoice("INV-DRAFT", new BigDecimal("800000.00"), AS_OF_DATE.minusDays(120));

        when(invoiceRepository.findOpenReceivables()).thenReturn(List.of(
                current,
                bucket30,
                bucket60,
                bucket90,
                over90,
                paid,
                draft
        ));
        when(snapshotRepository.findByPeriod("2026-07")).thenReturn(Optional.empty());
        when(snapshotRepository.save(any(ReceivableAgingSnapshot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReceivableAgingSnapshot snapshot = service.generateSnapshot(
                new GenerateReceivableAgingSnapshotRequest("2026-07", AS_OF_DATE, "closing piutang Juli"),
                "piutang.admin"
        );

        assertThat(snapshot.getPeriod()).isEqualTo("2026-07");
        assertThat(snapshot.getCurrentAmount()).isEqualByComparingTo("100000.00");
        assertThat(snapshot.getBucket30Amount()).isEqualByComparingTo("200000.00");
        assertThat(snapshot.getBucket60Amount()).isEqualByComparingTo("300000.00");
        assertThat(snapshot.getBucket90Amount()).isEqualByComparingTo("400000.00");
        assertThat(snapshot.getBucketOver90Amount()).isEqualByComparingTo("500000.00");
        assertThat(snapshot.getGeneratedAt()).isNotNull();
        verify(auditTrailService).record(
                "piutang.admin",
                "RECEIVABLE",
                "GENERATE_RECEIVABLE_AGING",
                snapshot.getId().toString(),
                "closing piutang Juli"
        );
    }

    @Test
    void updatesExistingSnapshotForSamePeriod() {
        ReceivableAgingSnapshot existing = new ReceivableAgingSnapshot(
                "2026-07",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                Instant.parse("2026-07-15T00:00:00Z")
        );
        Invoice invoice = issuedInvoice("INV-OPEN", new BigDecimal("125000.00"), AS_OF_DATE.minusDays(10));

        when(invoiceRepository.findOpenReceivables()).thenReturn(List.of(invoice));
        when(snapshotRepository.findByPeriod("2026-07")).thenReturn(Optional.of(existing));
        when(snapshotRepository.save(any(ReceivableAgingSnapshot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReceivableAgingSnapshot snapshot = service.generateSnapshot(
                new GenerateReceivableAgingSnapshotRequest("2026-07", AS_OF_DATE, "refresh"),
                "piutang.admin"
        );

        assertThat(snapshot).isSameAs(existing);
        assertThat(snapshot.getCurrentAmount()).isEqualByComparingTo("0.00");
        assertThat(snapshot.getBucket30Amount()).isEqualByComparingTo("125000.00");
        assertThat(snapshot.getBucket60Amount()).isEqualByComparingTo("0.00");
    }

    private static Invoice issuedInvoice(String invoiceNumber, BigDecimal amount, LocalDate dueDate) {
        Invoice invoice = draftInvoice(invoiceNumber, amount, dueDate);
        invoice.markIssued(ISSUED_AT);
        return invoice;
    }

    private static Invoice draftInvoice(String invoiceNumber, BigDecimal amount, LocalDate dueDate) {
        return new Invoice(
                UUID.randomUUID(),
                UUID.randomUUID(),
                invoiceNumber,
                "2026-07",
                amount,
                dueDate
        );
    }
}
