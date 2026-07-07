package id.pdam.sia.payment.web;

import id.pdam.sia.payment.domain.PaymentStatus;
import id.pdam.sia.payment.domain.PaymentWebhookStatus;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.Instant;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentControllerPermissionTest {
    @Test
    void paymentSettlementEndpointsRequireGranularPermissions() throws NoSuchMethodException {
        assertPermission(
                PaymentSettlementController.class.getMethod(
                        "settleCounterPayment",
                        String.class,
                        SettleCounterPaymentRequest.class,
                        Principal.class
                ),
                "hasAuthority('payment.counter')"
        );
        assertPermission(
                PaymentSettlementController.class.getMethod(
                        "reversePayment",
                        UUID.class,
                        ReversePaymentRequest.class,
                        Principal.class
                ),
                "hasAuthority('payment.reverse')"
        );
    }

    @Test
    void paymentWebhookEventListRequiresReadPermission() throws NoSuchMethodException {
        assertPermission(
                PaymentWebhookController.class.getMethod(
                        "listEvents",
                        String.class,
                        PaymentWebhookStatus.class,
                        int.class,
                        int.class
                ),
                "hasAuthority('payment.webhook.read')"
        );
    }

    @Test
    void paymentReadEndpointsRequireReadPermission() throws NoSuchMethodException {
        assertPermission(
                PaymentQueryController.class.getMethod(
                        "listPayments",
                        PaymentStatus.class,
                        String.class,
                        int.class,
                        int.class
                ),
                "hasAuthority('payment.read')"
        );
        assertPermission(
                PaymentQueryController.class.getMethod("getPayment", UUID.class),
                "hasAuthority('payment.read')"
        );
    }

    @Test
    void paymentReconciliationEndpointsRequireReconcilePermission() throws NoSuchMethodException {
        assertPermission(
                PaymentReconciliationController.class.getMethod(
                        "exportPayments",
                        PaymentStatus.class,
                        String.class,
                        Instant.class,
                        Instant.class,
                        Principal.class
                ),
                "hasAuthority('payment.reconcile')"
        );
        assertPermission(
                PaymentReconciliationController.class.getMethod(
                        "matchBankStatement",
                        PaymentReconciliationMatchRequest.class,
                        Principal.class
                ),
                "hasAuthority('payment.reconcile')"
        );
    }

    @Test
    void providerWebhookIntakeRemainsHmacValidatedWithoutUserRbacGate() throws NoSuchMethodException {
        Method receiveWebhook = PaymentWebhookController.class.getMethod(
                "receiveWebhook",
                String.class,
                PaymentWebhookRequest.class
        );

        assertThat(receiveWebhook.getAnnotation(PreAuthorize.class))
                .as("provider callbacks must stay protected by HMAC signature, not user Basic auth")
                .isNull();
    }

    private static void assertPermission(Method method, String expectedExpression) {
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation)
                .as(method.getName() + " must have @PreAuthorize")
                .isNotNull();
        assertThat(annotation.value()).isEqualTo(expectedExpression);
    }
}
