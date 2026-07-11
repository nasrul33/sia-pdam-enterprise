package id.pdam.sia.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakAuthorityConverterTest {
    @Test
    void keycloakRolesAndPermissionClaimsBecomeApplicationAuthorities() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("admin")
                .issuedAt(Instant.parse("2026-07-11T00:00:00Z"))
                .expiresAt(Instant.parse("2026-07-11T01:00:00Z"))
                .claim("scope", "openid profile")
                .claim("realm_access", Map.of("roles", List.of("finance-supervisor")))
                .claim("resource_access", Map.of(
                        "sia-pdam",
                        Map.of("roles", List.of("billing-officer"))
                ))
                .claim("permissions", List.of("journal.post", "invoice.issue"))
                .build();

        Collection<GrantedAuthority> authorities = new KeycloakAuthorityConverter("sia-pdam").convert(jwt);

        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .contains(
                        "ROLE_FINANCE_SUPERVISOR",
                        "ROLE_BILLING_OFFICER",
                        "journal.post",
                        "invoice.issue",
                        "SCOPE_openid",
                        "SCOPE_profile"
                );
    }

    @Test
    void malformedOptionalClaimsAreIgnoredWithoutCreatingAuthorities() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("admin")
                .claim("realm_access", "invalid")
                .claim("resource_access", Map.of("sia-pdam", "invalid"))
                .claim("permissions", List.of("", "  "))
                .build();

        assertThat(new KeycloakAuthorityConverter("sia-pdam").convert(jwt)).isEmpty();
    }
}
