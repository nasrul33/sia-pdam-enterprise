package id.pdam.sia.billing.application;

import id.pdam.sia.billing.domain.TariffBlock;
import id.pdam.sia.billing.domain.TariffVersion;
import id.pdam.sia.billing.domain.TariffVersionStatus;
import id.pdam.sia.billing.repository.TariffBlockRepository;
import id.pdam.sia.billing.repository.TariffVersionRepository;
import id.pdam.sia.billing.web.CalculateTariffRequest;
import id.pdam.sia.billing.web.CreateTariffBlockRequest;
import id.pdam.sia.billing.web.CreateTariffVersionRequest;
import id.pdam.sia.billing.web.TariffWorkflowRequest;
import id.pdam.sia.connection.repository.TariffGroupRepository;
import id.pdam.sia.shared.audit.AuditTrailService;
import id.pdam.sia.shared.exception.BusinessException;
import id.pdam.sia.shared.money.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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

class TariffEngineApplicationServiceTest {
    private final TariffVersionRepository tariffVersionRepository = mock(TariffVersionRepository.class);
    private final TariffBlockRepository tariffBlockRepository = mock(TariffBlockRepository.class);
    private final TariffGroupRepository tariffGroupRepository = mock(TariffGroupRepository.class);
    private final AuditTrailService auditTrailService = mock(AuditTrailService.class);

    private final TariffEngineApplicationService service = new TariffEngineApplicationService(
            tariffVersionRepository,
            tariffBlockRepository,
            tariffGroupRepository,
            auditTrailService
    );

    @Test
    void createsDraftTariffVersionWithAuditTrail() {
        UUID tariffGroupId = UUID.randomUUID();
        when(tariffGroupRepository.existsById(tariffGroupId)).thenReturn(true);
        when(tariffVersionRepository.existsByTariffGroupIdAndEffectiveDate(tariffGroupId, LocalDate.of(2026, 7, 1)))
                .thenReturn(false);
        when(tariffVersionRepository.save(any(TariffVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TariffVersion version = service.createVersion(
                new CreateTariffVersionRequest(tariffGroupId, LocalDate.of(2026, 7, 1), "tarif juli"),
                "billing.admin"
        );

        assertThat(version.getTariffGroupId()).isEqualTo(tariffGroupId);
        assertThat(version.getStatus()).isEqualTo(TariffVersionStatus.DRAFT);
        verify(auditTrailService).record(
                "billing.admin",
                "BILLING",
                "CREATE_TARIFF_VERSION",
                version.getId().toString(),
                "tarif juli"
        );
    }

    @Test
    void rejectsDuplicateEffectiveDateForTariffGroup() {
        UUID tariffGroupId = UUID.randomUUID();
        LocalDate effectiveDate = LocalDate.of(2026, 7, 1);
        when(tariffGroupRepository.existsById(tariffGroupId)).thenReturn(true);
        when(tariffVersionRepository.existsByTariffGroupIdAndEffectiveDate(tariffGroupId, effectiveDate)).thenReturn(true);

        assertThatThrownBy(() -> service.createVersion(
                new CreateTariffVersionRequest(tariffGroupId, effectiveDate, "duplikat"),
                "billing.admin"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void addsTariffBlockToDraftVersionWithAuditTrail() {
        TariffVersion version = draftVersion();
        when(tariffVersionRepository.findById(version.getId())).thenReturn(Optional.of(version));
        when(tariffBlockRepository.existsByTariffVersionIdAndBlockOrder(version.getId(), 1)).thenReturn(false);
        when(tariffBlockRepository.save(any(TariffBlock.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TariffBlock block = service.addBlock(
                version.getId(),
                new CreateTariffBlockRequest(
                        1,
                        new BigDecimal("0.000"),
                        new BigDecimal("10.000"),
                        new BigDecimal("2500.00"),
                        "blok pertama"
                ),
                "billing.admin"
        );

        assertThat(block.getBlockOrder()).isEqualTo(1);
        assertThat(block.getPricePerM3()).isEqualByComparingTo("2500.00");
        verify(auditTrailService).record(
                "billing.admin",
                "BILLING",
                "ADD_TARIFF_BLOCK",
                block.getId().toString(),
                "blok pertama"
        );
    }

    @Test
    void rejectsAddingBlockToActiveVersion() {
        TariffVersion version = draftVersion();
        version.activate();
        when(tariffVersionRepository.findById(version.getId())).thenReturn(Optional.of(version));

        assertThatThrownBy(() -> service.addBlock(
                version.getId(),
                new CreateTariffBlockRequest(
                        1,
                        new BigDecimal("0.000"),
                        new BigDecimal("10.000"),
                        new BigDecimal("2500.00"),
                        "blok pertama"
                ),
                "billing.admin"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("draft");
    }

    @Test
    void activatesDraftVersionWhenBlocksAreContiguous() {
        TariffVersion version = draftVersion();
        when(tariffVersionRepository.findById(version.getId())).thenReturn(Optional.of(version));
        when(tariffBlockRepository.findByTariffVersionIdOrderByBlockOrderAsc(version.getId())).thenReturn(List.of(
                block(version.getId(), 1, "0.000", "10.000", "2500.00"),
                block(version.getId(), 2, "10.000", "20.000", "3500.00"),
                block(version.getId(), 3, "20.000", null, "5000.00")
        ));

        TariffVersion activated = service.activateVersion(
                version.getId(),
                new TariffWorkflowRequest("berlaku juli").reason(),
                "billing.spv"
        );

        assertThat(activated.getStatus()).isEqualTo(TariffVersionStatus.ACTIVE);
        verify(auditTrailService).record(
                "billing.spv",
                "BILLING",
                "ACTIVATE_TARIFF_VERSION",
                version.getId().toString(),
                "berlaku juli"
        );
    }

    @Test
    void rejectsActivationWhenBlocksHaveGap() {
        TariffVersion version = draftVersion();
        when(tariffVersionRepository.findById(version.getId())).thenReturn(Optional.of(version));
        when(tariffBlockRepository.findByTariffVersionIdOrderByBlockOrderAsc(version.getId())).thenReturn(List.of(
                block(version.getId(), 1, "0.000", "10.000", "2500.00"),
                block(version.getId(), 2, "12.000", null, "5000.00")
        ));

        assertThatThrownBy(() -> service.activateVersion(version.getId(), "validasi tarif", "billing.spv"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("contiguous");
    }

    @Test
    void calculatesProgressiveTariffFromLatestActiveEffectiveVersion() {
        UUID tariffGroupId = UUID.randomUUID();
        TariffVersion older = activeVersion(tariffGroupId, LocalDate.of(2026, 1, 1));
        TariffVersion latest = activeVersion(tariffGroupId, LocalDate.of(2026, 7, 1));

        when(tariffVersionRepository.findFirstByTariffGroupIdAndStatusAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
                tariffGroupId,
                TariffVersionStatus.ACTIVE,
                LocalDate.of(2026, 7, 31)
        )).thenReturn(Optional.of(latest));
        when(tariffBlockRepository.findByTariffVersionIdOrderByBlockOrderAsc(latest.getId())).thenReturn(List.of(
                block(latest.getId(), 1, "0.000", "10.000", "2500.00"),
                block(latest.getId(), 2, "10.000", "20.000", "3500.00"),
                block(latest.getId(), 3, "20.000", null, "5000.00")
        ));

        TariffCalculationResult result = service.calculate(new CalculateTariffRequest(
                tariffGroupId,
                LocalDate.of(2026, 7, 31),
                new BigDecimal("24.500")
        ));

        assertThat(result.tariffVersionId()).isEqualTo(latest.getId());
        assertThat(older.getEffectiveDate()).isBefore(latest.getEffectiveDate());
        assertThat(result.usageM3()).isEqualByComparingTo("24.500");
        assertThat(result.subtotal()).isEqualTo(Money.of("82500.00"));
        assertThat(result.lines()).hasSize(3);
        assertThat(result.lines().get(0).quantityM3()).isEqualByComparingTo("10.000");
        assertThat(result.lines().get(0).amount()).isEqualTo(Money.of("25000.00"));
        assertThat(result.lines().get(1).quantityM3()).isEqualByComparingTo("10.000");
        assertThat(result.lines().get(1).amount()).isEqualTo(Money.of("35000.00"));
        assertThat(result.lines().get(2).quantityM3()).isEqualByComparingTo("4.500");
        assertThat(result.lines().get(2).amount()).isEqualTo(Money.of("22500.00"));
    }

    @Test
    void rejectsCalculationWhenNoActiveTariffVersionExists() {
        UUID tariffGroupId = UUID.randomUUID();
        LocalDate billingDate = LocalDate.of(2026, 7, 31);
        when(tariffVersionRepository.findFirstByTariffGroupIdAndStatusAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
                tariffGroupId,
                TariffVersionStatus.ACTIVE,
                billingDate
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.calculate(new CalculateTariffRequest(
                tariffGroupId,
                billingDate,
                BigDecimal.TEN
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No active tariff version");
    }

    private static TariffVersion draftVersion() {
        return new TariffVersion(UUID.randomUUID(), LocalDate.of(2026, 7, 1));
    }

    private static TariffVersion activeVersion(UUID tariffGroupId, LocalDate effectiveDate) {
        TariffVersion version = new TariffVersion(tariffGroupId, effectiveDate);
        version.activate();
        return version;
    }

    private static TariffBlock block(
            UUID tariffVersionId,
            int blockOrder,
            String minM3,
            String maxM3,
            String pricePerM3
    ) {
        return new TariffBlock(
                tariffVersionId,
                blockOrder,
                new BigDecimal(minM3),
                maxM3 == null ? null : new BigDecimal(maxM3),
                new BigDecimal(pricePerM3)
        );
    }
}
