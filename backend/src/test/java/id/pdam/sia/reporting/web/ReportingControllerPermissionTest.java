package id.pdam.sia.reporting.web;

import id.pdam.sia.reporting.application.PaymentReconciliationReviewStatus;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.Instant;
import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReportingControllerPermissionTest {
    @Test
    void bankReconciliationEvidenceEndpointsRequirePaymentReconcilePermission() throws NoSuchMethodException {
        assertPermission(
                ReportingController.class.getMethod("paymentReconciliationEvidence", UUID.class),
                "hasAuthority('payment.reconcile')"
        );
        assertPermission(
                ReportingController.class.getMethod("exportPaymentReconciliationEvidence", UUID.class),
                "hasAuthority('payment.reconcile')"
        );
    }

    @Test
    void bankReconciliationReviewRegisterRequiresPaymentReconcilePermission() throws NoSuchMethodException {
        assertPermission(
                ReportingController.class.getMethod(
                        "paymentReconciliationReviewRegister",
                        PaymentReconciliationReviewStatus.class,
                        Instant.class,
                        Instant.class,
                        int.class,
                        int.class
                ),
                "hasAuthority('payment.reconcile')"
        );
        assertPermission(
                ReportingController.class.getMethod(
                        "exportPaymentReconciliationReviewRegister",
                        PaymentReconciliationReviewStatus.class,
                        Instant.class,
                        Instant.class
                ),
                "hasAuthority('payment.reconcile')"
        );
    }

    private static void assertPermission(Method method, String expectedExpression) {
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation)
                .as(method.getName() + " must have @PreAuthorize")
                .isNotNull();
        assertThat(annotation.value()).isEqualTo(expectedExpression);
    }
}
