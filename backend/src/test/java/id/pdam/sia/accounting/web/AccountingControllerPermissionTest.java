package id.pdam.sia.accounting.web;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccountingControllerPermissionTest {
    @Test
    void accountingCommandEndpointsRequireGranularPermissions() throws NoSuchMethodException {
        assertPermission(
                method("createAccount", CreateAccountRequest.class, Principal.class),
                "hasAuthority('account.manage')"
        );
        assertPermission(
                method("createAccountingPeriod", CreateAccountingPeriodRequest.class, Principal.class),
                "hasAuthority('period.manage')"
        );
        assertPermission(
                method("startClosingReview", UUID.class, WorkflowReasonRequest.class, Principal.class),
                "hasAuthority('period.close')"
        );
        assertPermission(
                method("lockPeriod", UUID.class, WorkflowReasonRequest.class, Principal.class),
                "hasAuthority('period.close')"
        );
        assertPermission(
                method("createJournal", CreateJournalRequest.class, Principal.class),
                "hasAuthority('journal.create')"
        );
        assertPermission(
                method("postJournal", UUID.class, PostJournalRequest.class, Principal.class),
                "hasAuthority('journal.post')"
        );
    }

    private static Method method(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return AccountingController.class.getMethod(name, parameterTypes);
    }

    private static void assertPermission(Method method, String expectedExpression) {
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation)
                .as(method.getName() + " must have @PreAuthorize")
                .isNotNull();
        assertThat(annotation.value()).isEqualTo(expectedExpression);
    }
}
