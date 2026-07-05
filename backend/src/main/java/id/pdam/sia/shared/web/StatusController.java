package id.pdam.sia.shared.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class StatusController {
    @GetMapping("/api/status")
    public Map<String, Object> status() {
        return Map.of(
                "status", "ok",
                "service", "sia-pdam-backend",
                "timestamp", Instant.now().toString()
        );
    }
}
