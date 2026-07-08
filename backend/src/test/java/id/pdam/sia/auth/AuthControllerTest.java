package id.pdam.sia.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

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
    void currentUserReturnsAnonymousStateWithoutAuthorities() {
        AuthController controller = new AuthController();
        AnonymousAuthenticationToken authentication = new AnonymousAuthenticationToken(
                "anonymous",
                "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        );

        CurrentUserResponse response = controller.currentUser(authentication);

        assertThat(response.username()).isNull();
        assertThat(response.authenticated()).isFalse();
        assertThat(response.authorities()).isEmpty();
    }
}
