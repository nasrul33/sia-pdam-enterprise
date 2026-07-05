package id.pdam.sia.metering.application;

import id.pdam.sia.connection.domain.Connection;
import id.pdam.sia.connection.domain.ConnectionStatus;
import id.pdam.sia.connection.repository.ConnectionRepository;
import id.pdam.sia.metering.domain.MeterReading;
import id.pdam.sia.metering.domain.MeterReadingStatus;
import id.pdam.sia.metering.domain.MeterRoute;
import id.pdam.sia.metering.repository.MeterReadingRepository;
import id.pdam.sia.metering.repository.MeterRouteRepository;
import id.pdam.sia.metering.web.CreateMeterReadingRequest;
import id.pdam.sia.metering.web.CreateMeterRouteRequest;
import id.pdam.sia.shared.audit.AuditTrailService;
import id.pdam.sia.shared.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class MeteringApplicationService {
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM");
    private static final int MAX_PAGE_SIZE = 100;

    private final MeterRouteRepository meterRouteRepository;
    private final MeterReadingRepository meterReadingRepository;
    private final ConnectionRepository connectionRepository;
    private final AuditTrailService auditTrailService;

    public MeteringApplicationService(
            MeterRouteRepository meterRouteRepository,
            MeterReadingRepository meterReadingRepository,
            ConnectionRepository connectionRepository,
            AuditTrailService auditTrailService
    ) {
        this.meterRouteRepository = meterRouteRepository;
        this.meterReadingRepository = meterReadingRepository;
        this.connectionRepository = connectionRepository;
        this.auditTrailService = auditTrailService;
    }

    @Transactional(readOnly = true)
    public Page<MeterRoute> listRoutes(String areaCode, int page, int size) {
        Pageable pageable = pageable(page, size, Sort.by("routeCode").ascending());
        String normalizedAreaCode = normalizeOptional(areaCode);
        if (normalizedAreaCode != null) {
            return meterRouteRepository.findByAreaCode(normalizedAreaCode, pageable);
        }
        return meterRouteRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public MeterRoute getRoute(UUID routeId) {
        return meterRouteRepository.findById(routeId)
                .orElseThrow(() -> new BusinessException("METER_ROUTE_NOT_FOUND", "Meter route was not found."));
    }

    @Transactional
    public MeterRoute createRoute(CreateMeterRouteRequest request, String actor) {
        String routeCode = requireNormalize(
                request.routeCode(),
                "METER_ROUTE_CODE_REQUIRED",
                "Meter route code is required."
        );
        meterRouteRepository.findByRouteCode(routeCode).ifPresent(existing -> {
            throw new BusinessException("METER_ROUTE_CODE_DUPLICATE", "Meter route code already exists.");
        });

        MeterRoute route = meterRouteRepository.save(new MeterRoute(routeCode, request.name(), request.areaCode()));
        auditTrailService.record(actor, "METERING", "CREATE_METER_ROUTE", route.getId().toString(), request.reason());
        return route;
    }

    @Transactional(readOnly = true)
    public Page<MeterReading> listReadings(
            UUID routeId,
            String period,
            MeterReadingStatus status,
            int page,
            int size
    ) {
        Pageable pageable = pageable(page, size, Sort.by("readAt").descending());
        String normalizedPeriod = normalizeOptionalPeriod(period);
        if (routeId != null && normalizedPeriod != null && status != null) {
            return meterReadingRepository.findByRouteIdAndPeriodAndStatus(routeId, normalizedPeriod, status, pageable);
        }
        if (routeId != null && normalizedPeriod != null) {
            return meterReadingRepository.findByRouteIdAndPeriod(routeId, normalizedPeriod, pageable);
        }
        if (routeId != null && status != null) {
            return meterReadingRepository.findByRouteIdAndStatus(routeId, status, pageable);
        }
        if (normalizedPeriod != null && status != null) {
            return meterReadingRepository.findByPeriodAndStatus(normalizedPeriod, status, pageable);
        }
        if (routeId != null) {
            return meterReadingRepository.findByRouteId(routeId, pageable);
        }
        if (normalizedPeriod != null) {
            return meterReadingRepository.findByPeriod(normalizedPeriod, pageable);
        }
        if (status != null) {
            return meterReadingRepository.findByStatus(status, pageable);
        }
        return meterReadingRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public MeterReading getReading(UUID readingId) {
        return meterReadingRepository.findById(readingId)
                .orElseThrow(() -> new BusinessException("METER_READING_NOT_FOUND", "Meter reading was not found."));
    }

    @Transactional
    public MeterReading createReading(CreateMeterReadingRequest request, String actor) {
        String period = normalizePeriod(request.period());
        Connection connection = connectionRepository.findById(request.connectionId())
                .orElseThrow(() -> new BusinessException("CONNECTION_NOT_FOUND", "Connection was not found."));
        if (connection.getStatus() != ConnectionStatus.ACTIVE) {
            throw new BusinessException("CONNECTION_NOT_ACTIVE", "Meter reading can only be created for active connection.");
        }
        if (!meterRouteRepository.existsById(request.routeId())) {
            throw new BusinessException("METER_ROUTE_NOT_FOUND", "Meter route was not found.");
        }
        if (meterReadingRepository.existsByConnectionIdAndPeriod(request.connectionId(), period)) {
            throw new BusinessException("METER_READING_PERIOD_DUPLICATE", "Meter reading for this connection and period already exists.");
        }

        MeterReading reading = meterReadingRepository.save(new MeterReading(
                request.connectionId(),
                request.routeId(),
                period,
                request.previousReading(),
                request.currentReading(),
                request.readAt(),
                request.readerId(),
                request.anomalyFlag(),
                request.anomalyReason()
        ));
        auditTrailService.record(actor, "METERING", "CREATE_METER_READING", reading.getId().toString(), request.reason());
        return reading;
    }

    @Transactional
    public MeterReading submitReading(UUID readingId, String reason, String actor) {
        MeterReading reading = getReadingForMutation(readingId);
        reading.submit();
        auditTrailService.record(actor, "METERING", "SUBMIT_METER_READING", reading.getId().toString(), reason);
        return reading;
    }

    @Transactional
    public MeterReading verifyReading(UUID readingId, String reason, String actor) {
        MeterReading reading = getReadingForMutation(readingId);
        reading.verify();
        auditTrailService.record(actor, "METERING", "VERIFY_METER_READING", reading.getId().toString(), reason);
        return reading;
    }

    @Transactional
    public MeterReading rejectReading(UUID readingId, String reason, String actor) {
        MeterReading reading = getReadingForMutation(readingId);
        reading.reject();
        auditTrailService.record(actor, "METERING", "REJECT_METER_READING", reading.getId().toString(), reason);
        return reading;
    }

    private MeterReading getReadingForMutation(UUID readingId) {
        return meterReadingRepository.findById(readingId)
                .orElseThrow(() -> new BusinessException("METER_READING_NOT_FOUND", "Meter reading was not found."));
    }

    private static Pageable pageable(int page, int size, Sort sort) {
        if (page < 0) {
            throw new BusinessException("PAGE_INVALID", "Page must be zero or greater.");
        }
        if (size < 1) {
            throw new BusinessException("PAGE_SIZE_INVALID", "Page size must be at least one.");
        }
        return PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), sort);
    }

    private static String requireNormalize(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String normalizeOptionalPeriod(String period) {
        if (period == null || period.isBlank()) {
            return null;
        }
        return normalizePeriod(period);
    }

    private static String normalizePeriod(String period) {
        String normalized = requireNormalize(period, "METER_READING_PERIOD_REQUIRED", "Meter reading period is required.");
        try {
            YearMonth.parse(normalized, PERIOD_FORMATTER);
        } catch (DateTimeException exception) {
            throw new BusinessException("METER_READING_PERIOD_INVALID", "Meter reading period must use yyyy-MM format.");
        }
        return normalized;
    }
}
