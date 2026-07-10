package id.pdam.sia.metering.application;

import id.pdam.sia.connection.domain.Connection;
import id.pdam.sia.connection.repository.ConnectionRepository;
import id.pdam.sia.metering.domain.MeterReading;
import id.pdam.sia.metering.domain.MeterReadingImportItemStatus;
import id.pdam.sia.metering.domain.MeterReadingStatus;
import id.pdam.sia.metering.domain.MeterRoute;
import id.pdam.sia.metering.repository.MeterReadingImportBatchRepository;
import id.pdam.sia.metering.repository.MeterReadingImportItemRepository;
import id.pdam.sia.metering.repository.MeterReadingRepository;
import id.pdam.sia.metering.repository.MeterRouteRepository;
import id.pdam.sia.metering.web.CreateMeterReadingRequest;
import id.pdam.sia.metering.web.CreateMeterRouteRequest;
import id.pdam.sia.metering.web.ImportMeterReadingRowRequest;
import id.pdam.sia.metering.web.ImportMeterReadingsRequest;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MeteringApplicationServiceTest {
    private static final Instant READ_AT = Instant.parse("2026-07-05T08:00:00Z");

    private final MeterRouteRepository meterRouteRepository = mock(MeterRouteRepository.class);
    private final MeterReadingRepository meterReadingRepository = mock(MeterReadingRepository.class);
    private final MeterReadingImportBatchRepository meterReadingImportBatchRepository = mock(MeterReadingImportBatchRepository.class);
    private final MeterReadingImportItemRepository meterReadingImportItemRepository = mock(MeterReadingImportItemRepository.class);
    private final ConnectionRepository connectionRepository = mock(ConnectionRepository.class);
    private final AuditTrailService auditTrailService = mock(AuditTrailService.class);

    private final MeteringApplicationService service = new MeteringApplicationService(
            meterRouteRepository,
            meterReadingRepository,
            meterReadingImportBatchRepository,
            meterReadingImportItemRepository,
            connectionRepository,
            auditTrailService
    );

    @Test
    void createsRouteWithAuditTrail() {
        when(meterRouteRepository.findByRouteCode("RUTE-01")).thenReturn(Optional.empty());
        when(meterRouteRepository.save(any(MeterRoute.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MeterRoute route = service.createRoute(
                new CreateMeterRouteRequest(" RUTE-01 ", "Rute Tengah Kota", "AREA-01", "setup rute"),
                "meter.admin"
        );

        assertThat(route.getRouteCode()).isEqualTo("RUTE-01");
        assertThat(route.getAreaCode()).isEqualTo("AREA-01");
        verify(auditTrailService).record(
                "meter.admin",
                "METERING",
                "CREATE_METER_ROUTE",
                route.getId().toString(),
                "setup rute"
        );
    }

    @Test
    void rejectsDuplicateRouteCode() {
        when(meterRouteRepository.findByRouteCode("RUTE-01")).thenReturn(Optional.of(new MeterRoute(
                "RUTE-01",
                "Rute Tengah Kota",
                "AREA-01"
        )));

        assertThatThrownBy(() -> service.createRoute(
                new CreateMeterRouteRequest("RUTE-01", "Rute Tengah Kota", "AREA-01", "setup rute"),
                "meter.admin"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createsDraftReadingForActiveConnectionAndCalculatesUsage() {
        Connection connection = activeConnection();
        UUID routeId = UUID.randomUUID();

        when(connectionRepository.findById(connection.getId())).thenReturn(Optional.of(connection));
        when(meterRouteRepository.existsById(routeId)).thenReturn(true);
        when(meterReadingRepository.existsByConnectionIdAndPeriod(connection.getId(), "2026-07")).thenReturn(false);
        when(meterReadingRepository.save(any(MeterReading.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MeterReading reading = service.createReading(
                readingRequest(connection.getId(), routeId, new BigDecimal("12.000"), new BigDecimal("18.500")),
                "meter.reader"
        );

        assertThat(reading.getPeriod()).isEqualTo("2026-07");
        assertThat(reading.getStatus()).isEqualTo(MeterReadingStatus.DRAFT);
        assertThat(reading.getUsageM3()).isEqualByComparingTo("6.500");
        verify(auditTrailService).record(
                "meter.reader",
                "METERING",
                "CREATE_METER_READING",
                reading.getId().toString(),
                "input baca meter"
        );
    }

    @Test
    void rejectsReadingForInactiveConnection() {
        Connection connection = new Connection(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "SR-0001",
                "MTR-0001",
                LocalDate.of(2026, 7, 5)
        );
        UUID routeId = UUID.randomUUID();

        when(connectionRepository.findById(connection.getId())).thenReturn(Optional.of(connection));

        assertThatThrownBy(() -> service.createReading(
                readingRequest(connection.getId(), routeId, BigDecimal.ZERO, BigDecimal.TEN),
                "meter.reader"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("active connection");
    }

    @Test
    void rejectsDuplicateConnectionPeriod() {
        Connection connection = activeConnection();
        UUID routeId = UUID.randomUUID();

        when(connectionRepository.findById(connection.getId())).thenReturn(Optional.of(connection));
        when(meterRouteRepository.existsById(routeId)).thenReturn(true);
        when(meterReadingRepository.existsByConnectionIdAndPeriod(connection.getId(), "2026-07")).thenReturn(true);

        assertThatThrownBy(() -> service.createReading(
                readingRequest(connection.getId(), routeId, BigDecimal.ZERO, BigDecimal.TEN),
                "meter.reader"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void rejectsCurrentReadingLowerThanPrevious() {
        Connection connection = activeConnection();
        UUID routeId = UUID.randomUUID();

        when(connectionRepository.findById(connection.getId())).thenReturn(Optional.of(connection));
        when(meterRouteRepository.existsById(routeId)).thenReturn(true);
        when(meterReadingRepository.existsByConnectionIdAndPeriod(connection.getId(), "2026-07")).thenReturn(false);

        assertThatThrownBy(() -> service.createReading(
                readingRequest(connection.getId(), routeId, new BigDecimal("20.000"), new BigDecimal("10.000")),
                "meter.reader"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Current reading cannot be lower");
    }

    @Test
    void rejectsInvalidPeriodMonth() {
        Connection connection = activeConnection();
        UUID routeId = UUID.randomUUID();

        assertThatThrownBy(() -> service.createReading(
                new CreateMeterReadingRequest(
                        connection.getId(),
                        routeId,
                        "2026-13",
                        BigDecimal.ZERO,
                        BigDecimal.TEN,
                        READ_AT,
                        null,
                        false,
                        null,
                        "input baca meter"
                ),
                "meter.reader"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("yyyy-MM");
    }

    @Test
    void runsReadingLifecycleWithAuditTrail() {
        MeterReading reading = reading();
        when(meterReadingRepository.findById(reading.getId())).thenReturn(Optional.of(reading));

        service.submitReading(reading.getId(), "kirim hasil baca", "meter.reader");
        service.rejectReading(reading.getId(), "foto buram", "meter.spv");
        service.submitReading(reading.getId(), "kirim ulang", "meter.reader");
        service.verifyReading(reading.getId(), "valid", "meter.spv");
        service.lockReading(reading.getId(), "kunci untuk billing", "meter.spv");

        assertThat(reading.getStatus()).isEqualTo(MeterReadingStatus.LOCKED);
        assertThat(reading.getLockedAt()).isNotNull();
        assertThat(reading.getLockedBy()).isEqualTo("meter.spv");
        verify(auditTrailService).record(
                "meter.reader",
                "METERING",
                "SUBMIT_METER_READING",
                reading.getId().toString(),
                "kirim hasil baca"
        );
        verify(auditTrailService).record(
                "meter.spv",
                "METERING",
                "REJECT_METER_READING",
                reading.getId().toString(),
                "foto buram"
        );
        verify(auditTrailService).record(
                "meter.reader",
                "METERING",
                "SUBMIT_METER_READING",
                reading.getId().toString(),
                "kirim ulang"
        );
        verify(auditTrailService).record(
                "meter.spv",
                "METERING",
                "VERIFY_METER_READING",
                reading.getId().toString(),
                "valid"
        );
        verify(auditTrailService).record(
                "meter.spv",
                "METERING",
                "LOCK_METER_READING",
                reading.getId().toString(),
                "kunci untuk billing"
        );
    }

    @Test
    void rejectsVerifyForDraftReading() {
        MeterReading reading = reading();
        when(meterReadingRepository.findById(reading.getId())).thenReturn(Optional.of(reading));

        assertThatThrownBy(() -> service.verifyReading(reading.getId(), "valid", "meter.spv"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only submitted");
    }

    @Test
    void rejectsLockForUnverifiedReading() {
        MeterReading reading = reading();
        when(meterReadingRepository.findById(reading.getId())).thenReturn(Optional.of(reading));

        assertThatThrownBy(() -> service.lockReading(reading.getId(), "lock draft", "meter.spv"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only verified");
    }

    @Test
    void importsOfflineRowsWithImportedSkippedAndInvalidResultItems() {
        Connection importedConnection = activeConnection("SR-0001");
        UUID duplicateConnectionId = UUID.randomUUID();
        UUID invalidConnectionId = UUID.randomUUID();
        UUID unknownConnectionId = UUID.randomUUID();
        UUID routeId = UUID.randomUUID();

        when(meterRouteRepository.existsById(routeId)).thenReturn(true);
        when(meterReadingImportBatchRepository.findBySourceBatchReference("DEVICE-01-202607"))
                .thenReturn(Optional.empty());
        when(meterReadingImportBatchRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(meterReadingImportItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(meterReadingImportItemRepository.findByBatchIdOrderByRowNumberAsc(any()))
                .thenAnswer(invocation -> List.of());
        when(meterReadingRepository.existsByConnectionIdAndPeriod(importedConnection.getId(), "2026-07")).thenReturn(false);
        when(meterReadingRepository.existsByConnectionIdAndPeriod(duplicateConnectionId, "2026-07")).thenReturn(true);
        when(meterReadingRepository.existsByConnectionIdAndPeriod(invalidConnectionId, "2026-07")).thenReturn(false);
        when(meterReadingRepository.existsByConnectionIdAndPeriod(unknownConnectionId, "2026-07")).thenReturn(false);
        when(connectionRepository.findById(importedConnection.getId())).thenReturn(Optional.of(importedConnection));
        when(connectionRepository.findById(invalidConnectionId)).thenReturn(Optional.of(activeConnection("SR-0003")));
        when(connectionRepository.findById(unknownConnectionId)).thenReturn(Optional.empty());
        when(meterReadingRepository.save(any(MeterReading.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MeteringApplicationService.MeterReadingImportResult result = service.importOfflineReadings(
                new ImportMeterReadingsRequest(
                        "DEVICE-01",
                        "DEVICE-01-202607",
                        routeId,
                        "2026-07",
                        List.of(
                                new ImportMeterReadingRowRequest(
                                        importedConnection.getId(),
                                        new BigDecimal("10.000"),
                                        new BigDecimal("15.500"),
                                        READ_AT,
                                        null,
                                        false,
                                        null
                                ),
                                new ImportMeterReadingRowRequest(
                                        duplicateConnectionId,
                                        BigDecimal.ZERO,
                                        BigDecimal.TEN,
                                        READ_AT,
                                        null,
                                        false,
                                        null
                                ),
                                new ImportMeterReadingRowRequest(
                                        invalidConnectionId,
                                        new BigDecimal("20.000"),
                                        new BigDecimal("10.000"),
                                        READ_AT,
                                        null,
                                        false,
                                        null
                                ),
                                new ImportMeterReadingRowRequest(
                                        unknownConnectionId,
                                        BigDecimal.ZERO,
                                        BigDecimal.TEN,
                                        READ_AT,
                                        null,
                                        false,
                                        null
                                )
                        ),
                        "sync offline device"
                ),
                "meter.reader"
        );

        assertThat(result.batch().getTotalRows()).isEqualTo(4);
        assertThat(result.batch().getImportedRows()).isEqualTo(1);
        assertThat(result.batch().getSkippedRows()).isEqualTo(1);
        assertThat(result.batch().getInvalidRows()).isEqualTo(2);
        verify(meterReadingImportItemRepository).save(org.mockito.ArgumentMatchers.argThat(item ->
                item.getStatus() == MeterReadingImportItemStatus.IMPORTED && item.getRowNumber() == 1
        ));
        verify(meterReadingImportItemRepository).save(org.mockito.ArgumentMatchers.argThat(item ->
                item.getStatus() == MeterReadingImportItemStatus.SKIPPED && item.getRowNumber() == 2
        ));
        verify(meterReadingImportItemRepository).save(org.mockito.ArgumentMatchers.argThat(item ->
                item.getStatus() == MeterReadingImportItemStatus.INVALID && item.getRowNumber() == 3
        ));
        verify(meterReadingImportItemRepository).save(org.mockito.ArgumentMatchers.argThat(item ->
                item.getStatus() == MeterReadingImportItemStatus.INVALID
                        && item.getRowNumber() == 4
                        && "CONNECTION_NOT_FOUND".equals(item.getCode())
        ));
        verify(auditTrailService).record(
                org.mockito.ArgumentMatchers.eq("meter.reader"),
                org.mockito.ArgumentMatchers.eq("METERING"),
                org.mockito.ArgumentMatchers.eq("IMPORT_OFFLINE_METER_READINGS"),
                org.mockito.ArgumentMatchers.eq(result.batch().getId().toString()),
                org.mockito.ArgumentMatchers.contains("imported=1;skipped=1;invalid=2")
        );
    }

    private static Connection activeConnection() {
        return activeConnection("SR-0001");
    }

    private static Connection activeConnection(String connectionNumber) {
        Connection connection = new Connection(
                UUID.randomUUID(),
                UUID.randomUUID(),
                connectionNumber,
                "MTR-" + connectionNumber,
                LocalDate.of(2026, 7, 5)
        );
        connection.activate();
        return connection;
    }

    private static MeterReading reading() {
        return new MeterReading(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "2026-07",
                BigDecimal.ZERO,
                new BigDecimal("12.000"),
                READ_AT,
                null,
                false,
                null
        );
    }

    private static CreateMeterReadingRequest readingRequest(
            UUID connectionId,
            UUID routeId,
            BigDecimal previousReading,
            BigDecimal currentReading
    ) {
        return new CreateMeterReadingRequest(
                connectionId,
                routeId,
                "2026-07",
                previousReading,
                currentReading,
                READ_AT,
                null,
                false,
                null,
                "input baca meter"
        );
    }
}
