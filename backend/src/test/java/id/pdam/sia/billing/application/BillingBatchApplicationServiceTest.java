package id.pdam.sia.billing.application;

import id.pdam.sia.billing.domain.BillingBatch;
import id.pdam.sia.billing.domain.BillingBatchStatus;
import id.pdam.sia.billing.domain.Invoice;
import id.pdam.sia.billing.domain.InvoiceLine;
import id.pdam.sia.billing.domain.InvoiceStatus;
import id.pdam.sia.billing.repository.BillingBatchRepository;
import id.pdam.sia.billing.repository.InvoiceLineRepository;
import id.pdam.sia.billing.repository.InvoiceRepository;
import id.pdam.sia.billing.web.GenerateBillingBatchRequest;
import id.pdam.sia.connection.domain.Connection;
import id.pdam.sia.connection.repository.ConnectionRepository;
import id.pdam.sia.metering.domain.MeterReading;
import id.pdam.sia.metering.repository.MeterReadingRepository;
import id.pdam.sia.shared.audit.AuditTrailService;
import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.idempotency.IdempotencyRecord;
import id.pdam.sia.shared.idempotency.IdempotencyService;
import id.pdam.sia.shared.money.CurrencyCode;
import id.pdam.sia.shared.money.Money;
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

class BillingBatchApplicationServiceTest {
    private static final Instant READ_AT = Instant.parse("2026-07-31T10:00:00Z");
    private static final LocalDate BILLING_DATE = LocalDate.of(2026, 7, 31);
    private static final LocalDate DUE_DATE = LocalDate.of(2026, 8, 20);

    private final BillingBatchRepository billingBatchRepository = mock(BillingBatchRepository.class);
    private final InvoiceRepository invoiceRepository = mock(InvoiceRepository.class);
    private final InvoiceLineRepository invoiceLineRepository = mock(InvoiceLineRepository.class);
    private final MeterReadingRepository meterReadingRepository = mock(MeterReadingRepository.class);
    private final ConnectionRepository connectionRepository = mock(ConnectionRepository.class);
    private final TariffEngineApplicationService tariffEngineApplicationService = mock(TariffEngineApplicationService.class);
    private final IdempotencyService idempotencyService = mock(IdempotencyService.class);
    private final AuditTrailService auditTrailService = mock(AuditTrailService.class);

    private final BillingBatchApplicationService service = new BillingBatchApplicationService(
            billingBatchRepository,
            invoiceRepository,
            invoiceLineRepository,
            meterReadingRepository,
            connectionRepository,
            tariffEngineApplicationService,
            idempotencyService,
            auditTrailService
    );

    @Test
    void generatesDraftInvoicesFromVerifiedReadingsWithIdempotencyAndAuditTrail() {
        UUID tariffGroupId = UUID.randomUUID();
        Connection connection = activeConnection(tariffGroupId, "SR-0001");
        MeterReading reading = verifiedReading(connection.getId(), new BigDecimal("18.500"));
        IdempotencyRecord idempotencyRecord = IdempotencyRecord.reserve(
                "bill-202607-area01",
                "BILLING_BATCH",
                "sha256:batch",
                Instant.now().plusSeconds(3600)
        );

        when(idempotencyService.reserve(eq("bill-202607-area01"), eq("BILLING_BATCH"), any(String.class), any(Instant.class)))
                .thenReturn(idempotencyRecord);
        when(billingBatchRepository.findByPeriodAndAreaCode("2026-07", "AREA-01")).thenReturn(Optional.empty());
        when(meterReadingRepository.findVerifiedByAreaCodeAndPeriod("AREA-01", "2026-07")).thenReturn(List.of(reading));
        when(connectionRepository.findById(connection.getId())).thenReturn(Optional.of(connection));
        when(invoiceRepository.existsByConnectionIdAndPeriod(connection.getId(), "2026-07")).thenReturn(false);
        when(tariffEngineApplicationService.calculate(any())).thenReturn(tariffResult(tariffGroupId, reading.getUsageM3()));
        when(billingBatchRepository.save(any(BillingBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(invoiceLineRepository.save(any(InvoiceLine.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BillingBatchGenerationResult result = service.generateBatch(
                new GenerateBillingBatchRequest("2026-07", "AREA-01", BILLING_DATE, DUE_DATE, "generate tagihan"),
                "bill-202607-area01",
                "billing.admin"
        );

        assertThat(result.batch().getStatus()).isEqualTo(BillingBatchStatus.COMPLETED);
        assertThat(result.batch().getBatchNumber()).isEqualTo("BB-202607-AREA-01");
        assertThat(result.generatedInvoices()).hasSize(1);
        assertThat(result.generatedInvoices().getFirst().getStatus()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(result.generatedInvoices().getFirst().getInvoiceNumber()).isEqualTo("INV-202607-SR-0001");
        assertThat(result.generatedInvoices().getFirst().getSubtotal()).isEqualByComparingTo("46250.00");
        assertThat(result.totalAmount()).isEqualTo(Money.of("46250.00"));
        assertThat(idempotencyRecord.isCompleted()).isTrue();

        ArgumentCaptor<InvoiceLine> lineCaptor = ArgumentCaptor.forClass(InvoiceLine.class);
        verify(invoiceLineRepository).save(lineCaptor.capture());
        assertThat(lineCaptor.getValue().getQuantity()).isEqualByComparingTo("18.500");
        assertThat(lineCaptor.getValue().getAmount()).isEqualByComparingTo("46250.00");
        verify(auditTrailService).record(
                "billing.admin",
                "BILLING",
                "GENERATE_BILLING_BATCH",
                result.batch().getId().toString(),
                "generate tagihan"
        );
    }

    @Test
    void returnsExistingBatchWhenIdempotencyKeyAlreadyCompleted() {
        BillingBatch existingBatch = new BillingBatch("BB-202607-AREA-01", "2026-07", "AREA-01", "bill-202607-area01");
        existingBatch.markCompleted();
        IdempotencyRecord idempotencyRecord = IdempotencyRecord.reserve(
                "bill-202607-area01",
                "BILLING_BATCH",
                "sha256:batch",
                Instant.now().plusSeconds(3600)
        );
        idempotencyRecord.markCompleted(existingBatch.getId().toString());

        when(idempotencyService.reserve(eq("bill-202607-area01"), eq("BILLING_BATCH"), any(String.class), any(Instant.class)))
                .thenReturn(idempotencyRecord);
        when(billingBatchRepository.findById(existingBatch.getId())).thenReturn(Optional.of(existingBatch));
        when(invoiceRepository.findByBillingBatchId(existingBatch.getId())).thenReturn(List.of());

        BillingBatchGenerationResult result = service.generateBatch(
                new GenerateBillingBatchRequest("2026-07", "AREA-01", BILLING_DATE, DUE_DATE, "retry"),
                "bill-202607-area01",
                "billing.admin"
        );

        assertThat(result.batch()).isSameAs(existingBatch);
        verify(meterReadingRepository, never()).findVerifiedByAreaCodeAndPeriod(any(), any());
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void rejectsWhenBatchAlreadyExistsForPeriodAreaWithDifferentIdempotencyKey() {
        IdempotencyRecord idempotencyRecord = IdempotencyRecord.reserve(
                "bill-202607-area01-new",
                "BILLING_BATCH",
                "sha256:batch-new",
                Instant.now().plusSeconds(3600)
        );
        when(idempotencyService.reserve(eq("bill-202607-area01-new"), eq("BILLING_BATCH"), any(String.class), any(Instant.class)))
                .thenReturn(idempotencyRecord);
        when(billingBatchRepository.findByPeriodAndAreaCode("2026-07", "AREA-01")).thenReturn(Optional.of(
                new BillingBatch("BB-202607-AREA-01", "2026-07", "AREA-01", "bill-202607-area01")
        ));

        assertThatThrownBy(() -> service.generateBatch(
                new GenerateBillingBatchRequest("2026-07", "AREA-01", BILLING_DATE, DUE_DATE, "duplikat"),
                "bill-202607-area01-new",
                "billing.admin"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void rejectsWhenNoVerifiedReadingsExistForAreaPeriod() {
        IdempotencyRecord idempotencyRecord = IdempotencyRecord.reserve(
                "bill-202607-area01",
                "BILLING_BATCH",
                "sha256:batch",
                Instant.now().plusSeconds(3600)
        );
        when(idempotencyService.reserve(eq("bill-202607-area01"), eq("BILLING_BATCH"), any(String.class), any(Instant.class)))
                .thenReturn(idempotencyRecord);
        when(billingBatchRepository.findByPeriodAndAreaCode("2026-07", "AREA-01")).thenReturn(Optional.empty());
        when(meterReadingRepository.findVerifiedByAreaCodeAndPeriod("AREA-01", "2026-07")).thenReturn(List.of());

        assertThatThrownBy(() -> service.generateBatch(
                new GenerateBillingBatchRequest("2026-07", "AREA-01", BILLING_DATE, DUE_DATE, "kosong"),
                "bill-202607-area01",
                "billing.admin"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No verified meter readings");
    }

    @Test
    void rejectsWhenInvoiceAlreadyExistsForConnectionPeriod() {
        UUID tariffGroupId = UUID.randomUUID();
        Connection connection = activeConnection(tariffGroupId, "SR-0001");
        MeterReading reading = verifiedReading(connection.getId(), BigDecimal.TEN);
        IdempotencyRecord idempotencyRecord = IdempotencyRecord.reserve(
                "bill-202607-area01",
                "BILLING_BATCH",
                "sha256:batch",
                Instant.now().plusSeconds(3600)
        );
        when(idempotencyService.reserve(eq("bill-202607-area01"), eq("BILLING_BATCH"), any(String.class), any(Instant.class)))
                .thenReturn(idempotencyRecord);
        when(billingBatchRepository.findByPeriodAndAreaCode("2026-07", "AREA-01")).thenReturn(Optional.empty());
        when(meterReadingRepository.findVerifiedByAreaCodeAndPeriod("AREA-01", "2026-07")).thenReturn(List.of(reading));
        when(connectionRepository.findById(connection.getId())).thenReturn(Optional.of(connection));
        when(invoiceRepository.existsByConnectionIdAndPeriod(connection.getId(), "2026-07")).thenReturn(true);

        assertThatThrownBy(() -> service.generateBatch(
                new GenerateBillingBatchRequest("2026-07", "AREA-01", BILLING_DATE, DUE_DATE, "duplikat invoice"),
                "bill-202607-area01",
                "billing.admin"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invoice already exists");
    }

    private static Connection activeConnection(UUID tariffGroupId, String connectionNumber) {
        Connection connection = new Connection(
                UUID.randomUUID(),
                tariffGroupId,
                connectionNumber,
                "MTR-" + connectionNumber,
                LocalDate.of(2026, 1, 15)
        );
        connection.activate();
        return connection;
    }

    private static MeterReading verifiedReading(UUID connectionId, BigDecimal usageM3) {
        MeterReading reading = new MeterReading(
                connectionId,
                UUID.randomUUID(),
                "2026-07",
                BigDecimal.ZERO,
                usageM3,
                READ_AT,
                null,
                false,
                null
        );
        reading.submit();
        reading.verify();
        return reading;
    }

    private static TariffCalculationResult tariffResult(UUID tariffGroupId, BigDecimal usageM3) {
        return new TariffCalculationResult(
                UUID.randomUUID(),
                tariffGroupId,
                LocalDate.of(2026, 7, 1),
                BILLING_DATE,
                usageM3,
                List.of(new TariffCalculationLine(
                        1,
                        BigDecimal.ZERO,
                        null,
                        usageM3,
                        new BigDecimal("2500.00"),
                        Money.of(usageM3.multiply(new BigDecimal("2500.00")), CurrencyCode.IDR)
                )),
                Money.of(usageM3.multiply(new BigDecimal("2500.00")), CurrencyCode.IDR)
        );
    }
}
