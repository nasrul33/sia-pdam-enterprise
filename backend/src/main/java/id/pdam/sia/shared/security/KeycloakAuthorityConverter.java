package id.pdam.sia.shared.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class KeycloakAuthorityConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
    private final String clientId;
    private final JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();

    public KeycloakAuthorityConverter(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("Keycloak client id is required.");
        }
        this.clientId = clientId.trim();
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, GrantedAuthority> authorities = new LinkedHashMap<>();
        Collection<GrantedAuthority> scopes = scopeConverter.convert(jwt);
        if (scopes != null) {
            scopes.forEach(authority -> authorities.put(authority.getAuthority(), authority));
        }
        addRoles(authorities, rolesFromAccessClaim(jwt.getClaim("realm_access")));
        addRoles(authorities, clientRoles(jwt.getClaim("resource_access")));
        addPermissions(authorities, jwt.getClaim("permissions"));
        return List.copyOf(authorities.values());
    }

    private Collection<?> clientRoles(Object resourceAccessClaim) {
        if (!(resourceAccessClaim instanceof Map<?, ?> resourceAccess)) {
            return List.of();
        }
        return rolesFromAccessClaim(resourceAccess.get(clientId));
    }

    private static Collection<?> rolesFromAccessClaim(Object accessClaim) {
        if (!(accessClaim instanceof Map<?, ?> access)) {
            return List.of();
        }
        Object roles = access.get("roles");
        return roles instanceof Collection<?> collection ? collection : List.of();
    }

    private static void addRoles(Map<String, GrantedAuthority> target, Collection<?> roles) {
        roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .map(KeycloakAuthorityConverter::roleAuthority)
                .forEach(authority -> target.put(authority, new SimpleGrantedAuthority(authority)));
    }

    private static void addPermissions(Map<String, GrantedAuthority> target, Object permissionsClaim) {
        if (!(permissionsClaim instanceof Collection<?> permissions)) {
            return;
        }
        permissions.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(String::trim)
                .filter(permission -> !permission.isBlank())
                .forEach(permission -> target.put(permission, new SimpleGrantedAuthority(permission)));
    }

    private static String roleAuthority(String role) {
        String normalized = role.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return "ROLE_" + normalized;
    }
}
