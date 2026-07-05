package id.pdam.sia.receivable.web;

import id.pdam.sia.receivable.domain.CollectionActionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CollectionActionControllerPermissionTest {
    @Test
    void collectionActionEndpointsRequireGranularPermissions() throws NoSuchMethodException {
        assertPermission(
                method("listActions", CollectionActionStatus.class, UUID.class, UUID.class, int.class, int.class),
                "hasAuthority('collection-action.read')"
        );
        assertPermission(
                method("getAction", UUID.class),
                "hasAuthority('collection-action.read')"
        );
        assertPermission(
                method("createAction", CreateCollectionActionRequest.class, Principal.class),
                "hasAuthority('collection-action.create')"
        );
        assertPermission(
                method("startAction", UUID.class, CollectionActionWorkflowRequest.class, Principal.class),
                "hasAuthority('collection-action.execute')"
        );
        assertPermission(
                method("completeAction", UUID.class, CollectionActionWorkflowRequest.class, Principal.class),
                "hasAuthority('collection-action.execute')"
        );
        assertPermission(
                method("cancelAction", UUID.class, CollectionActionWorkflowRequest.class, Principal.class),
                "hasAuthority('collection-action.cancel')"
        );
    }

    private static Method method(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return CollectionActionController.class.getMethod(name, parameterTypes);
    }

    private static void assertPermission(Method method, String expectedExpression) {
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation)
                .as(method.getName() + " must have @PreAuthorize")
                .isNotNull();
        assertThat(annotation.value()).isEqualTo(expectedExpression);
    }
}
