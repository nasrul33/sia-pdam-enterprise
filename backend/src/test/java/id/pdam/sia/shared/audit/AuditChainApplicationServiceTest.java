package id.pdam.sia.shared.audit;

import id.pdam.sia.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditChainApplicationServiceTest {
    private final AuditChainEntryRepository auditChainEntryRepository = mock(AuditChainEntryRepository.class);
    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final AuditChainApplicationService service = new AuditChainApplicationService(
            auditChainEntryRepository,
            auditLogRepository
    );

    @Test
    void appendCreatesHashLinkedEntryAndSkipsDuplicateAuditLog() {
        AuditLog auditLog = auditLog("finance.manager", "ACCOUNTING", "POST_JOURNAL", "JV-1", "post journal");
        when(auditChainEntryRepository.existsByAuditLogId(auditLog.getId())).thenReturn(false);
        when(auditChainEntryRepository.findTopByOrderBySequenceNoDesc()).thenReturn(Optional.empty());

        service.append(auditLog);

        ArgumentCaptor<AuditChainEntry> entryCaptor = ArgumentCaptor.forClass(AuditChainEntry.class);
        verify(auditChainEntryRepository).save(entryCaptor.capture());
        assertThat(entryCaptor.getValue().getAuditLogId()).isEqualTo(auditLog.getId());
        assertThat(entryCaptor.getValue().getPreviousHash()).isNull();
        assertThat(entryCaptor.getValue().getEntryHash()).hasSize(64);

        when(auditChainEntryRepository.existsByAuditLogId(auditLog.getId())).thenReturn(true);
        service.append(auditLog);
        verify(auditChainEntryRepository).save(any(AuditChainEntry.class));
    }

    @Test
    void verifyReturnsOkForIntactChainAndBreaksOnEntryHashMismatch() {
        AuditLog firstLog = auditLog("finance.manager", "ACCOUNTING", "POST_JOURNAL", "JV-1", "post journal");
        AuditLog secondLog = auditLog("auditor", "PAYMENT", "RECONCILE_BANK_DAILY", "REC-1", "review");

        when(auditChainEntryRepository.existsByAuditLogId(firstLog.getId())).thenReturn(false);
        when(auditChainEntryRepository.findTopByOrderBySequenceNoDesc()).thenReturn(Optional.empty());
        service.append(firstLog);
        ArgumentCaptor<AuditChainEntry> entryCaptor = ArgumentCaptor.forClass(AuditChainEntry.class);
        verify(auditChainEntryRepository).save(entryCaptor.capture());
        AuditChainEntry firstEntry = entryCaptor.getValue();
        setSequenceNo(firstEntry, 1L);

        when(auditChainEntryRepository.existsByAuditLogId(secondLog.getId())).thenReturn(false);
        when(auditChainEntryRepository.findTopByOrderBySequenceNoDesc()).thenReturn(Optional.of(firstEntry));
        service.append(secondLog);
        verify(auditChainEntryRepository, times(2)).save(entryCaptor.capture());
        List<AuditChainEntry> captured = entryCaptor.getAllValues();
        AuditChainEntry secondEntry = captured.get(captured.size() - 1);
        setSequenceNo(secondEntry, 2L);

        when(auditChainEntryRepository.findAllByOrderBySequenceNoAsc()).thenReturn(List.of(firstEntry, secondEntry));
        when(auditLogRepository.findAllById(any())).thenReturn(List.of(firstLog, secondLog));

        AuditChainApplicationService.AuditChainVerificationReport ok = service.verify();

        assertThat(ok.valid()).isTrue();
        assertThat(ok.entriesChecked()).isEqualTo(2);
        assertThat(ok.status()).isEqualTo("OK");

        AuditChainEntry tamperedSecondEntry = new AuditChainEntry(secondLog.getId(), firstEntry.getEntryHash(), "bad-hash");
        setSequenceNo(tamperedSecondEntry, 2L);
        when(auditChainEntryRepository.findAllByOrderBySequenceNoAsc()).thenReturn(List.of(firstEntry, tamperedSecondEntry));

        AuditChainApplicationService.AuditChainVerificationReport broken = service.verify();

        assertThat(broken.valid()).isFalse();
        assertThat(broken.firstBreakSequence()).isEqualTo(2L);
        assertThat(broken.status()).isEqualTo("ENTRY_HASH_MISMATCH");
    }

    @Test
    void appendRejectsNullAuditLog() {
        assertThatThrownBy(() -> service.append(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Audit log is required");

        verify(auditChainEntryRepository, never()).save(any(AuditChainEntry.class));
    }

    private static AuditLog auditLog(String actor, String module, String action, String recordId, String reason) {
        return AuditLog.from(AuditTrailEntry.sensitiveAction(actor, module, action, recordId, reason));
    }

    private static void setSequenceNo(AuditChainEntry entry, Long sequenceNo) {
        try {
            Field field = AuditChainEntry.class.getDeclaredField("sequenceNo");
            field.setAccessible(true);
            field.set(entry, sequenceNo);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not set audit chain sequence for test", exception);
        }
    }
}
