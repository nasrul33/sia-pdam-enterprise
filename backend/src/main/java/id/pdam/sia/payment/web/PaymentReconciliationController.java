package id.pdam.sia.payment.web;

import id.pdam.sia.payment.application.PaymentReconciliationApplicationService;
import id.pdam.sia.payment.application.PaymentReconciliationExportRow;
import id.pdam.sia.payment.application.PaymentReconciliationFilters;
import id.pdam.sia.payment.application.PaymentReconciliationMatchReport;
import id.pdam.sia.payment.domain.PaymentReconciliationSessionStatus;
import id.pdam.sia.payment.domain.PaymentStatus;
import id.pdam.sia.shared.security.Permissions;
import id.pdam.sia.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/payment-reconciliation")
public class PaymentReconciliationController {
    private static final MediaType TEXT_CSV = MediaType.parseMediaType("text/csv");

    private final PaymentReconciliationApplicationService paymentReconciliationApplicationService;

    public PaymentReconciliationController(PaymentReconciliationApplicationService paymentReconciliationApplicationService) {
        this.paymentReconciliationApplicationService = paymentReconciliationApplicationService;
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @PreAuthorize(Permissions.PAYMENT_RECONCILE)
    public ResponseEntity<String> exportPayments(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant paidAtFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant paidAtTo,
            Principal principal
    ) {
        List<PaymentReconciliationExportRow> rows = paymentReconciliationApplicationService.exportPayments(
                new PaymentReconciliationFilters(status, channel, paidAtFrom, paidAtTo),
                actor(principal)
        );

        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("payment-reconciliation.csv")
                        .build()
                        .toString())
                .body(csv(rows));
    }

    @PostMapping("/match")
    @PreAuthorize(Permissions.PAYMENT_RECONCILE)
    public PaymentReconciliationMatchReport matchBankStatement(
            @Valid @RequestBody PaymentReconciliationMatchRequest request,
            Principal principal
    ) {
        return paymentReconciliationApplicationService.matchBankStatementRows(request.toCommands(), actor(principal));
    }

    @GetMapping("/sessions")
    @PreAuthorize(Permissions.PAYMENT_RECONCILE)
    public PageResponse<PaymentReconciliationSessionSummaryResponse> listSessions(
            @RequestParam(required = false) PaymentReconciliationSessionStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size
    ) {
        return PageResponse.from(paymentReconciliationApplicationService.listSessions(status, page, size)
                .map(PaymentReconciliationSessionSummaryResponse::from));
    }

    @PostMapping("/sessions")
    @PreAuthorize(Permissions.PAYMENT_RECONCILE)
    public PaymentReconciliationSessionResponse createSession(
            @Valid @RequestBody CreatePaymentReconciliationSessionRequest request,
            Principal principal
    ) {
        return PaymentReconciliationSessionResponse.from(paymentReconciliationApplicationService.createSession(
                request.toCommands(),
                request.sourceFilename(),
                request.bankAccountReference(),
                actor(principal)
        ));
    }

    @GetMapping("/sessions/{sessionId}")
    @PreAuthorize(Permissions.PAYMENT_RECONCILE)
    public PaymentReconciliationSessionResponse getSession(@PathVariable UUID sessionId) {
        return PaymentReconciliationSessionResponse.from(paymentReconciliationApplicationService.getSession(sessionId));
    }

    @PostMapping("/sessions/{sessionId}/items/{itemId}/resolve")
    @PreAuthorize(Permissions.PAYMENT_RECONCILE)
    public PaymentReconciliationSessionResponse resolveItem(
            @PathVariable UUID sessionId,
            @PathVariable UUID itemId,
            @Valid @RequestBody ResolvePaymentReconciliationItemRequest request,
            Principal principal
    ) {
        paymentReconciliationApplicationService.resolveItem(
                sessionId,
                itemId,
                request.resolutionStatus(),
                request.reason(),
                actor(principal)
        );
        return PaymentReconciliationSessionResponse.from(paymentReconciliationApplicationService.getSession(sessionId));
    }

    @PostMapping("/sessions/{sessionId}/items/{itemId}/adjust")
    @PreAuthorize(Permissions.PAYMENT_RECONCILE_AND_JOURNAL_POST)
    public PaymentReconciliationSessionResponse createAdjustment(
            @PathVariable UUID sessionId,
            @PathVariable UUID itemId,
            @Valid @RequestBody CreatePaymentReconciliationAdjustmentRequest request,
            Principal principal
    ) {
        return PaymentReconciliationSessionResponse.from(paymentReconciliationApplicationService.createAdjustment(
                sessionId,
                itemId,
                request.toCommand(),
                actor(principal)
        ));
    }

    @PostMapping("/sessions/{sessionId}/complete")
    @PreAuthorize(Permissions.PAYMENT_RECONCILE)
    public PaymentReconciliationSessionResponse completeSession(
            @PathVariable UUID sessionId,
            @Valid @RequestBody CompletePaymentReconciliationSessionRequest request,
            Principal principal
    ) {
        return PaymentReconciliationSessionResponse.from(paymentReconciliationApplicationService.completeSession(
                sessionId,
                request.reason(),
                actor(principal)
        ));
    }

    private static String csv(List<PaymentReconciliationExportRow> rows) {
        StringBuilder builder = new StringBuilder();
        appendRow(
                builder,
                "payment_id",
                "payment_number",
                "channel",
                "external_reference",
                "status",
                "amount",
                "paid_at",
                "settled_at",
                "reversed_at",
                "settlement_journal_entry_id",
                "reversal_journal_entry_id"
        );

        rows.forEach(row -> appendRow(
                builder,
                row.paymentId(),
                row.paymentNumber(),
                row.channel(),
                row.externalReference(),
                row.status(),
                row.amount(),
                row.paidAt(),
                row.settledAt(),
                row.reversedAt(),
                row.settlementJournalEntryId(),
                row.reversalJournalEntryId()
        ));
        return builder.toString();
    }

    private static void appendRow(StringBuilder builder, Object... values) {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            appendValue(builder, values[index]);
        }
        builder.append('\n');
    }

    private static void appendValue(StringBuilder builder, Object value) {
        if (value == null) {
            return;
        }
        String text = value.toString();
        boolean quoted = text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r");
        if (!quoted) {
            builder.append(text);
            return;
        }

        builder.append('"');
        builder.append(text.replace("\"", "\"\""));
        builder.append('"');
    }

    private static String actor(Principal principal) {
        return principal == null ? "system" : principal.getName();
    }
}
