package id.pdam.sia.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuthControllerTest {
    @Test
    void currentUserReturnsAuthenticatedPrincipalAndSortedAuthorities() {
        AuthController controller = new AuthController();
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(
                "petugas.piutang",
                "n/a",
                List.of(
                        new SimpleGrantedAuthority("collection-action.read"),
                        new SimpleGrantedAuthority("ROLE_PETUGAS_PIUTANG"),
                        new SimpleGrantedAuthority("collection-action.create")
                )
        );

        CurrentUserResponse response = controller.currentUser(authentication);

        assertThat(response.username()).isEqualTo("petugas.piutang");
        assertThat(response.authenticated()).isTrue();
        assertThat(response.authorities()).containsExactly(
                "ROLE_PETUGAS_PIUTANG",
                "collection-action.create",
                "collection-action.read"
        );
    }

    @Test
    void currentUserEndpointRequiresAuthentication() throws NoSuchMethodException {
        Method method = AuthController.class.getMethod("currentUser", org.springframework.security.core.Authentication.class);

        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("isAuthenticated()");
    }
}
