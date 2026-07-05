package id.pdam.sia.payment.web;

import id.pdam.sia.payment.application.PaymentWebhookApplicationService;
import id.pdam.sia.payment.domain.PaymentWebhookStatus;
import id.pdam.sia.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api")
public class PaymentWebhookController {
    private final PaymentWebhookApplicationService paymentWebhookApplicationService;

    public PaymentWebhookController(PaymentWebhookApplicationService paymentWebhookApplicationService) {
        this.paymentWebhookApplicationService = paymentWebhookApplicationService;
    }

    @PostMapping("/payments/webhook")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PaymentWebhookResponse receiveWebhook(
            @RequestHeader("X-Payment-Signature") String signature,
            @Valid @RequestBody PaymentWebhookRequest request
    ) {
        return PaymentWebhookResponse.from(
                paymentWebhookApplicationService.receiveWebhook(request, signature, "payment-webhook")
        );
    }

    @GetMapping("/payment-webhook-events")
    public PageResponse<PaymentWebhookResponse> listEvents(
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) PaymentWebhookStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        return PageResponse.from(
                paymentWebhookApplicationService.listEvents(provider, status, page, size).map(PaymentWebhookResponse::from)
        );
    }
}
