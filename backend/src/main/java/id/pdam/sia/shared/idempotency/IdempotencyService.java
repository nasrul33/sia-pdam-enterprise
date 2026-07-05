package id.pdam.sia.shared.idempotency;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class IdempotencyService {
    private final IdempotencyRepository idempotencyRepository;

    public IdempotencyService(IdempotencyRepository idempotencyRepository) {
        this.idempotencyRepository = idempotencyRepository;
    }

    @Transactional
    public IdempotencyRecord reserve(String idempotencyKey, String module, String requestHash, Instant expiresAt) {
        return idempotencyRepository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> {
                    existing.ensureSameRequest(requestHash);
                    return existing;
                })
                .orElseGet(() -> saveNewRecord(idempotencyKey, module, requestHash, expiresAt));
    }

    private IdempotencyRecord saveNewRecord(String idempotencyKey, String module, String requestHash, Instant expiresAt) {
        try {
            return idempotencyRepository.save(IdempotencyRecord.reserve(idempotencyKey, module, requestHash, expiresAt));
        } catch (DataIntegrityViolationException exception) {
            IdempotencyRecord existing = idempotencyRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> exception);
            existing.ensureSameRequest(requestHash);
            return existing;
        }
    }
}
