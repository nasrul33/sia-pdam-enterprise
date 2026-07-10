package id.pdam.sia.payment.web;

import id.pdam.sia.payment.application.PaymentQueryApplicationService;
import id.pdam.sia.payment.domain.PaymentStatus;
import id.pdam.sia.shared.security.Permissions;
import id.pdam.sia.shared.web.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/payments")
public class PaymentQueryController {
    private final PaymentQueryApplicationService paymentQueryApplicationService;

    public PaymentQueryController(PaymentQueryApplicationService paymentQueryApplicationService) {
        this.paymentQueryApplicationService = paymentQueryApplicationService;
    }

    @GetMapping
    @PreAuthorize(Permissions.PAYMENT_READ)
    public PageResponse<PaymentSummaryResponse> listPayments(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) String channel,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        return PageResponse.from(paymentQueryApplicationService.listPayments(status, channel, page, size)
                .map(PaymentSummaryResponse::from));
    }

    @GetMapping("/{paymentId}")
    @PreAuthorize(Permissions.PAYMENT_READ)
    public PaymentSettlementResponse getPayment(@PathVariable UUID paymentId) {
        return PaymentSettlementResponse.from(paymentQueryApplicationService.getPayment(paymentId));
    }

    @GetMapping("/{paymentId}/receipt")
    @PreAuthorize(Permissions.PAYMENT_READ)
    public PaymentSettlementResponse getPaymentReceipt(@PathVariable UUID paymentId) {
        return PaymentSettlementResponse.from(paymentQueryApplicationService.getPayment(paymentId));
    }
}
