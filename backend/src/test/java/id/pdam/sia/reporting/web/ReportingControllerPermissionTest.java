package id.pdam.sia.reporting.web;

import id.pdam.sia.reporting.application.PaymentReconciliationReviewStatus;
import id.pdam.sia.reporting.domain.PaymentReconciliationHandoffStatus;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.Instant;
import java.time.LocalDate;
import java.lang.reflect.Method;
import java.security.Principal;
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

    @Test
    void bankReconciliationHandoffNotesUseReadAndMutationPermissions() throws NoSuchMethodException {
        assertPermission(
                ReportingController.class.getMethod(
                        "paymentReconciliationHandoffWorkload",
                        PaymentReconciliationHandoffStatus.class,
                        String.class,
                        boolean.class,
                        LocalDate.class,
                        LocalDate.class,
                        int.class,
                        int.class
                ),
                "hasAuthority('payment.reconcile')"
        );
        assertPermission(
                ReportingController.class.getMethod(
                        "exportPaymentReconciliationHandoffWorkload",
                        PaymentReconciliationHandoffStatus.class,
                        String.class,
                        boolean.class,
                        LocalDate.class,
                        LocalDate.class
                ),
                "hasAuthority('payment.reconcile')"
        );
        assertPermission(
                ReportingController.class.getMethod(
                        "paymentReconciliationHandoffOwnerSla",
                        PaymentReconciliationHandoffStatus.class,
                        String.class,
                        boolean.class,
                        LocalDate.class,
                        LocalDate.class
                ),
                "hasAuthority('payment.reconcile')"
        );
        assertPermission(
                ReportingController.class.getMethod(
                        "exportPaymentReconciliationHandoffOwnerSla",
                        PaymentReconciliationHandoffStatus.class,
                        String.class,
                        boolean.class,
                        LocalDate.class,
                        LocalDate.class
                ),
                "hasAuthority('payment.reconcile')"
        );
        assertPermission(
                ReportingController.class.getMethod(
                        "paymentReconciliationHandoffAgingBuckets",
                        PaymentReconciliationHandoffStatus.class,
                        String.class,
                        boolean.class,
                        LocalDate.class,
                        LocalDate.class
                ),
                "hasAuthority('payment.reconcile')"
        );
        assertPermission(
                ReportingController.class.getMethod(
                        "exportPaymentReconciliationHandoffAgingBuckets",
                        PaymentReconciliationHandoffStatus.class,
                        String.class,
                        boolean.class,
                        LocalDate.class,
                        LocalDate.class
                ),
                "hasAuthority('payment.reconcile')"
        );
        assertPermission(
                ReportingController.class.getMethod("paymentReconciliationHandoffNotes", UUID.class),
                "hasAuthority('payment.reconcile')"
        );
        assertPermission(
                ReportingController.class.getMethod(
                        "createPaymentReconciliationHandoffNote",
                        UUID.class,
                        PaymentReconciliationHandoffNoteRequest.class,
                        Principal.class
                ),
                "hasAuthority('payment.reconcile') and hasAuthority('payment.reconciliation.handoff-note')"
        );
        assertPermission(
                ReportingController.class.getMethod(
                        "revisePaymentReconciliationHandoffNote",
                        UUID.class,
                        UUID.class,
                        PaymentReconciliationHandoffNoteRequest.class,
                        Principal.class
                ),
                "hasAuthority('payment.reconcile') and hasAuthority('payment.reconciliation.handoff-note')"
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
