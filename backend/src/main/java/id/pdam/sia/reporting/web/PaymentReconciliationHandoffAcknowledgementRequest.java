package id.pdam.sia.reporting.web;

import id.pdam.sia.reporting.application.PaymentReconciliationHandoffAcknowledgementCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaymentReconciliationHandoffAcknowledgementRequest(
        @NotBlank @Size(max = 128) String packetScopeHash,
        @NotBlank @Size(max = 500) String reason
) {
    public PaymentReconciliationHandoffAcknowledgementCommand toCommand() {
        return new PaymentReconciliationHandoffAcknowledgementCommand(packetScopeHash, reason);
    }
}
