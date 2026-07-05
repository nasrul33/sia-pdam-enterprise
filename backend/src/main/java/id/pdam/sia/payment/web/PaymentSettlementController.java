package id.pdam.sia.payment.web;

import id.pdam.sia.payment.application.PaymentSettlementApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class PaymentSettlementController {
    private final PaymentSettlementApplicationService paymentSettlementApplicationService;

    public PaymentSettlementController(PaymentSettlementApplicationService paymentSettlementApplicationService) {
        this.paymentSettlementApplicationService = paymentSettlementApplicationService;
    }

    @PostMapping("/payments/counter")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public PaymentSettlementResponse settleCounterPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SettleCounterPaymentRequest request,
            Principal principal
    ) {
        return PaymentSettlementResponse.from(
                paymentSettlementApplicationService.settleCounterPayment(request, idempotencyKey, actor(principal))
        );
    }

    @PostMapping("/payments/{paymentId}/reverse")
    @PreAuthorize("isAuthenticated()")
    public PaymentSettlementResponse reversePayment(
            @PathVariable UUID paymentId,
            @Valid @RequestBody ReversePaymentRequest request,
            Principal principal
    ) {
        return PaymentSettlementResponse.from(
                paymentSettlementApplicationService.reversePayment(paymentId, request, actor(principal))
        );
    }

    private static String actor(Principal principal) {
        return principal == null ? "system" : principal.getName();
    }
}
