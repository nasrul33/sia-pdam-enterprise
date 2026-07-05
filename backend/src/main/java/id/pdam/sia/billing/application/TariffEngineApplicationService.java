package id.pdam.sia.billing.application;

import id.pdam.sia.billing.domain.TariffBlock;
import id.pdam.sia.billing.domain.TariffVersion;
import id.pdam.sia.billing.domain.TariffVersionStatus;
import id.pdam.sia.billing.repository.TariffBlockRepository;
import id.pdam.sia.billing.repository.TariffVersionRepository;
import id.pdam.sia.billing.web.CalculateTariffRequest;
import id.pdam.sia.billing.web.CreateTariffBlockRequest;
import id.pdam.sia.billing.web.CreateTariffVersionRequest;
import id.pdam.sia.connection.repository.TariffGroupRepository;
import id.pdam.sia.shared.audit.AuditTrailService;
import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.money.CurrencyCode;
import id.pdam.sia.shared.money.Money;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TariffEngineApplicationService {
    private static final int MAX_PAGE_SIZE = 100;

    private final TariffVersionRepository tariffVersionRepository;
    private final TariffBlockRepository tariffBlockRepository;
    private final TariffGroupRepository tariffGroupRepository;
    private final AuditTrailService auditTrailService;

    public TariffEngineApplicationService(
            TariffVersionRepository tariffVersionRepository,
            TariffBlockRepository tariffBlockRepository,
            TariffGroupRepository tariffGroupRepository,
            AuditTrailService auditTrailService
    ) {
        this.tariffVersionRepository = tariffVersionRepository;
        this.tariffBlockRepository = tariffBlockRepository;
        this.tariffGroupRepository = tariffGroupRepository;
        this.auditTrailService = auditTrailService;
    }

    @Transactional(readOnly = true)
    public Page<TariffVersion> listVersions(UUID tariffGroupId, TariffVersionStatus status, int page, int size) {
        Pageable pageable = pageable(page, size, Sort.by("effectiveDate").descending());
        if (tariffGroupId != null && status != null) {
            return tariffVersionRepository.findByTariffGroupIdAndStatus(tariffGroupId, status, pageable);
        }
        if (tariffGroupId != null) {
            return tariffVersionRepository.findByTariffGroupId(tariffGroupId, pageable);
        }
        if (status != null) {
            return tariffVersionRepository.findByStatus(status, pageable);
        }
        return tariffVersionRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public TariffVersion getVersion(UUID tariffVersionId) {
        return tariffVersionRepository.findById(tariffVersionId)
                .orElseThrow(() -> new BusinessException("TARIFF_VERSION_NOT_FOUND", "Tariff version was not found."));
    }

    @Transactional(readOnly = true)
    public List<TariffBlock> listBlocks(UUID tariffVersionId) {
        requireVersionExists(tariffVersionId);
        return tariffBlockRepository.findByTariffVersionIdOrderByBlockOrderAsc(tariffVersionId);
    }

    @Transactional
    public TariffVersion createVersion(CreateTariffVersionRequest request, String actor) {
        if (!tariffGroupRepository.existsById(request.tariffGroupId())) {
            throw new BusinessException("TARIFF_GROUP_NOT_FOUND", "Tariff group was not found.");
        }
        if (tariffVersionRepository.existsByTariffGroupIdAndEffectiveDate(request.tariffGroupId(), request.effectiveDate())) {
            throw new BusinessException("TARIFF_VERSION_EFFECTIVE_DATE_DUPLICATE", "Tariff version effective date already exists.");
        }

        TariffVersion version = tariffVersionRepository.save(new TariffVersion(request.tariffGroupId(), request.effectiveDate()));
        auditTrailService.record(actor, "BILLING", "CREATE_TARIFF_VERSION", version.getId().toString(), request.reason());
        return version;
    }

    @Transactional
    public TariffBlock addBlock(UUID tariffVersionId, CreateTariffBlockRequest request, String actor) {
        TariffVersion version = getVersionForMutation(tariffVersionId);
        if (!version.isDraft()) {
            throw new BusinessException("TARIFF_BLOCK_VERSION_NOT_DRAFT", "Tariff block can only be added to draft tariff version.");
        }
        if (tariffBlockRepository.existsByTariffVersionIdAndBlockOrder(tariffVersionId, request.blockOrder())) {
            throw new BusinessException("TARIFF_BLOCK_ORDER_DUPLICATE", "Tariff block order already exists.");
        }

        TariffBlock block = tariffBlockRepository.save(new TariffBlock(
                tariffVersionId,
                request.blockOrder(),
                request.minM3(),
                request.maxM3(),
                request.pricePerM3()
        ));
        auditTrailService.record(actor, "BILLING", "ADD_TARIFF_BLOCK", block.getId().toString(), request.reason());
        return block;
    }

    @Transactional
    public TariffVersion activateVersion(UUID tariffVersionId, String reason, String actor) {
        TariffVersion version = getVersionForMutation(tariffVersionId);
        List<TariffBlock> blocks = tariffBlockRepository.findByTariffVersionIdOrderByBlockOrderAsc(tariffVersionId);
        validateBlocks(blocks);
        version.activate();
        auditTrailService.record(actor, "BILLING", "ACTIVATE_TARIFF_VERSION", version.getId().toString(), reason);
        return version;
    }

    @Transactional
    public TariffVersion archiveVersion(UUID tariffVersionId, String reason, String actor) {
        TariffVersion version = getVersionForMutation(tariffVersionId);
        version.archive();
        auditTrailService.record(actor, "BILLING", "ARCHIVE_TARIFF_VERSION", version.getId().toString(), reason);
        return version;
    }

    @Transactional(readOnly = true)
    public TariffCalculationResult calculate(CalculateTariffRequest request) {
        BigDecimal usageM3 = requireNonNegative(request.usageM3(), "TARIFF_USAGE_REQUIRED", "Tariff usage is required.");
        TariffVersion version = tariffVersionRepository
                .findFirstByTariffGroupIdAndStatusAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
                        request.tariffGroupId(),
                        TariffVersionStatus.ACTIVE,
                        request.billingDate()
                )
                .orElseThrow(() -> new BusinessException("TARIFF_VERSION_ACTIVE_NOT_FOUND", "No active tariff version was found for tariff group and billing date."));

        List<TariffBlock> blocks = tariffBlockRepository.findByTariffVersionIdOrderByBlockOrderAsc(version.getId());
        validateBlocks(blocks);
        List<TariffCalculationLine> lines = calculateLines(usageM3, blocks);
        Money subtotal = lines.stream()
                .map(TariffCalculationLine::amount)
                .reduce(Money.zero(), Money::add);

        return new TariffCalculationResult(
                version.getId(),
                version.getTariffGroupId(),
                version.getEffectiveDate(),
                request.billingDate(),
                usageM3.stripTrailingZeros(),
                List.copyOf(lines),
                subtotal
        );
    }

    private TariffVersion getVersionForMutation(UUID tariffVersionId) {
        return tariffVersionRepository.findById(tariffVersionId)
                .orElseThrow(() -> new BusinessException("TARIFF_VERSION_NOT_FOUND", "Tariff version was not found."));
    }

    private void requireVersionExists(UUID tariffVersionId) {
        if (!tariffVersionRepository.existsById(tariffVersionId)) {
            throw new BusinessException("TARIFF_VERSION_NOT_FOUND", "Tariff version was not found.");
        }
    }

    private static void validateBlocks(List<TariffBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            throw new BusinessException("TARIFF_BLOCK_EMPTY", "Tariff version must have at least one block.");
        }

        BigDecimal expectedMin = BigDecimal.ZERO;
        int expectedOrder = 1;
        for (int index = 0; index < blocks.size(); index++) {
            TariffBlock block = blocks.get(index);
            if (block.getBlockOrder() != expectedOrder) {
                throw new BusinessException("TARIFF_BLOCK_ORDER_NOT_SEQUENTIAL", "Tariff block order must be sequential.");
            }
            if (block.getMinM3().compareTo(expectedMin) != 0) {
                throw new BusinessException("TARIFF_BLOCK_NOT_CONTIGUOUS", "Tariff blocks must be contiguous.");
            }
            boolean lastBlock = index == blocks.size() - 1;
            if (block.getMaxM3() == null && !lastBlock) {
                throw new BusinessException("TARIFF_BLOCK_UNBOUNDED_NOT_LAST", "Unbounded tariff block must be the last block.");
            }
            if (lastBlock && block.getMaxM3() != null) {
                throw new BusinessException("TARIFF_BLOCK_LAST_NOT_UNBOUNDED", "Last tariff block must be unbounded.");
            }
            if (block.getMaxM3() != null) {
                expectedMin = block.getMaxM3();
            }
            expectedOrder++;
        }
    }

    private static List<TariffCalculationLine> calculateLines(BigDecimal usageM3, List<TariffBlock> blocks) {
        List<TariffCalculationLine> lines = new ArrayList<>();
        for (TariffBlock block : blocks) {
            if (usageM3.compareTo(block.getMinM3()) <= 0) {
                break;
            }

            BigDecimal upperBound = block.getMaxM3() == null ? usageM3 : usageM3.min(block.getMaxM3());
            BigDecimal quantity = upperBound.subtract(block.getMinM3());
            if (quantity.signum() <= 0) {
                continue;
            }

            Money amount = Money.of(block.getPricePerM3(), CurrencyCode.IDR).multiply(quantity);
            lines.add(new TariffCalculationLine(
                    block.getBlockOrder(),
                    block.getMinM3(),
                    block.getMaxM3(),
                    quantity.stripTrailingZeros(),
                    block.getPricePerM3(),
                    amount
            ));
        }
        return lines;
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

    private static BigDecimal requireNonNegative(BigDecimal value, String code, String message) {
        if (value == null) {
            throw new BusinessException(code, message);
        }
        if (value.signum() < 0) {
            throw new BusinessException("TARIFF_USAGE_NEGATIVE", "Tariff usage cannot be negative.");
        }
        return value.stripTrailingZeros();
    }
}
