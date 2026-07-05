package id.pdam.sia.shared.web;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        Instant timestamp,
        String code,
        String message,
        List<String> details
) {
    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(Instant.now(), code, message, List.of());
    }
}
