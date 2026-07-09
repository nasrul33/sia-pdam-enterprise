package id.pdam.sia.billing.web;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BillingControllerPermissionTest {
    @Test
    void billingFinancialCommandEndpointsRequireGranularPermissions() throws NoSuchMethodException {
        assertPermission(
                method("generateBatch", String.class, GenerateBillingBatchRequest.class, Principal.class),
                "hasAuthority('billing.generate')"
        );
        assertPermission(
                method("issueInvoice", UUID.class, IssueInvoiceRequest.class, Principal.class),
                "hasAuthority('invoice.issue')"
        );
        assertPermission(
                method("getInvoiceDocument", UUID.class),
                "hasAuthority('invoice.view')"
        );
        assertPermission(
                method("voidInvoice", UUID.class, VoidInvoiceRequest.class, Principal.class),
                "hasAuthority('invoice.correct.approve')"
        );
    }

    private static Method method(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return BillingBatchController.class.getMethod(name, parameterTypes);
    }

    private static void assertPermission(Method method, String expectedExpression) {
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation)
                .as(method.getName() + " must have @PreAuthorize")
                .isNotNull();
        assertThat(annotation.value()).isEqualTo(expectedExpression);
    }
}
