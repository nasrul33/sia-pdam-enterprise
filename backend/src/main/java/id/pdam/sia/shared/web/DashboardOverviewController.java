package id.pdam.sia.shared.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardOverviewController {
    @GetMapping("/overview")
    public DashboardOverviewResponse overview() {
        return new DashboardOverviewResponse(
                Instant.now(),
                List.of(
                        new DashboardMetric("Accounting Core", "Ready", "Money, journal balance, and period lock guardrails are implemented.", "success"),
                        new DashboardMetric("Audit Trail", "Persisted", "Sensitive actions write to audit_logs with actor, module, action, and reason.", "success"),
                        new DashboardMetric("Payment Guard", "Idempotent", "idempotency_keys table enforces duplicate-safe payment workflows.", "success"),
                        new DashboardMetric("Open Questions", "10", "Business decisions are tracked before customer, billing, and payment go live.", "warning")
                ),
                List.of(
                        new ModuleHealth("Shared Kernel", "BackendFoundationAgent", "ready", "Money uses BigDecimal and audit trail is persisted."),
                        new ModuleHealth("Accounting", "AccountingAgent", "ready", "Posting requires balanced debit-credit and unlocked period."),
                        new ModuleHealth("Customer", "CustomerAgent", "planned", "Customer number must be unique before billing depends on it."),
                        new ModuleHealth("Connection", "CustomerAgent", "planned", "Connection lifecycle must be explicit and indexed."),
                        new ModuleHealth("Metering", "MeteringAgent", "planned", "Reading per connection and period must be unique."),
                        new ModuleHealth("Billing", "BillingAgent", "planned", "Invoice issue must create receivable and correction journal."),
                        new ModuleHealth("Payment", "PaymentAgent", "in_progress", "Every settlement path must reserve an idempotency key."),
                        new ModuleHealth("Reporting", "ReportingAgent", "planned", "Final reports must read posted ledger entries only.")
                ),
                List.of(
                        new QualityGate("Docker Compose", "docker compose config", "configured"),
                        new QualityGate("Backend", "gradle clean test bootJar", "configured"),
                        new QualityGate("Frontend Lint", "npm run lint", "configured"),
                        new QualityGate("Frontend Typecheck", "npm run typecheck", "configured"),
                        new QualityGate("Frontend Build", "npm run build", "configured")
                ),
                List.of(
                        new RiskItem("OQ-001", "Single PDAM vs multi-unit structure must be confirmed before production data model freeze.", "medium"),
                        new RiskItem("OQ-003", "Official numbering format for customer, connection, invoice, journal, and receipt is still open.", "high"),
                        new RiskItem("OQ-004", "Tariff block formula must be confirmed before billing batch implementation.", "high"),
                        new RiskItem("OQ-008", "Bank reconciliation channel is not final; payment integration remains generic.", "medium")
                )
        );
    }

    public record DashboardOverviewResponse(
            Instant generatedAt,
            List<DashboardMetric> metrics,
            List<ModuleHealth> modules,
            List<QualityGate> qualityGates,
            List<RiskItem> risks
    ) {
    }

    public record DashboardMetric(String label, String value, String helper, String tone) {
    }

    public record ModuleHealth(String module, String owner, String status, String guardrail) {
    }

    public record QualityGate(String name, String command, String status) {
    }

    public record RiskItem(String code, String description, String severity) {
    }
}
