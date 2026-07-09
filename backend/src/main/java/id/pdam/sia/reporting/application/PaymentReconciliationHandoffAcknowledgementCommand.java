package id.pdam.sia.reporting.application;

public record PaymentReconciliationHandoffAcknowledgementCommand(
        String packetScopeHash,
        String reason
) {
}
