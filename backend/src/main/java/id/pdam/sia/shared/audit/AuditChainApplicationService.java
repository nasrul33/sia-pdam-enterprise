package id.pdam.sia.shared.audit;

import id.pdam.sia.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AuditChainApplicationService {
    private final AuditChainEntryRepository auditChainEntryRepository;
    private final AuditLogRepository auditLogRepository;

    public AuditChainApplicationService(AuditChainEntryRepository auditChainEntryRepository, AuditLogRepository auditLogRepository) {
        this.auditChainEntryRepository = auditChainEntryRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(AuditLog auditLog) {
        if (auditLog == null) {
            throw new BusinessException("AUDIT_CHAIN_LOG_REQUIRED", "Audit log is required for chain append.");
        }
        if (auditChainEntryRepository.existsByAuditLogId(auditLog.getId())) {
            return;
        }
        String previousHash = auditChainEntryRepository.findTopByOrderBySequenceNoDesc()
                .map(AuditChainEntry::getEntryHash)
                .orElse(null);
        auditChainEntryRepository.save(new AuditChainEntry(auditLog.getId(), previousHash, hash(previousHash, auditLog)));
    }

    @Transactional(readOnly = true)
    public AuditChainVerificationReport verify() {
        List<AuditChainEntry> chain = auditChainEntryRepository.findAllByOrderBySequenceNoAsc();
        Map<UUID, AuditLog> logsById = auditLogRepository.findAllById(chain.stream().map(AuditChainEntry::getAuditLogId).toList())
                .stream()
                .collect(Collectors.toMap(AuditLog::getId, Function.identity()));
        String previousHash = null;
        for (AuditChainEntry entry : chain) {
            AuditLog log = logsById.get(entry.getAuditLogId());
            if (log == null) {
                return new AuditChainVerificationReport(false, chain.size(), entry.getSequenceNo(), "AUDIT_LOG_MISSING");
            }
            if (!equals(previousHash, entry.getPreviousHash())) {
                return new AuditChainVerificationReport(false, chain.size(), entry.getSequenceNo(), "PREVIOUS_HASH_MISMATCH");
            }
            String expected = hash(previousHash, log);
            if (!expected.equals(entry.getEntryHash())) {
                return new AuditChainVerificationReport(false, chain.size(), entry.getSequenceNo(), "ENTRY_HASH_MISMATCH");
            }
            previousHash = entry.getEntryHash();
        }
        return new AuditChainVerificationReport(true, chain.size(), null, "OK");
    }

    private static boolean equals(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    private static String hash(String previousHash, AuditLog log) {
        String payload = (previousHash == null ? "" : previousHash)
                + "|"
                + log.getId()
                + "|"
                + log.getActor()
                + "|"
                + log.getModule()
                + "|"
                + log.getAction()
                + "|"
                + nullSafe(log.getRecordId())
                + "|"
                + nullSafe(log.getReason())
                + "|"
                + log.getCreatedAt();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    public record AuditChainVerificationReport(boolean valid, int entriesChecked, Long firstBreakSequence, String status) {
    }
}
