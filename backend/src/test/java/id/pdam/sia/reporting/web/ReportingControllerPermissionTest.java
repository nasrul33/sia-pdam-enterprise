package id.pdam.sia.reporting.web;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

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

    private static void assertPermission(Method method, String expectedExpression) {
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation)
                .as(method.getName() + " must have @PreAuthorize")
                .isNotNull();
        assertThat(annotation.value()).isEqualTo(expectedExpression);
    }
}
