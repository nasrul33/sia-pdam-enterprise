package id.pdam.sia.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BootstrapAdminUserServiceTest {
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final BootstrapAdminUserService service = new BootstrapAdminUserService(jdbcTemplate, passwordEncoder);

    @Test
    void skipsWhenBootstrapAdminIsNotConfigured() {
        BootstrapAdminResult result = service.bootstrap(new BootstrapAdminProperties("", "", ""));

        assertThat(result).isEqualTo(BootstrapAdminResult.SKIPPED);
        verifyNoInteractions(jdbcTemplate, passwordEncoder);
    }

    @Test
    void rejectsPartialBootstrapAdminConfiguration() {
        BootstrapAdminProperties properties = new BootstrapAdminProperties("admin", "", "");

        assertThatThrownBy(() -> service.bootstrap(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Bootstrap admin requires username, email, and password when any bootstrap value is provided.");
        verifyNoInteractions(jdbcTemplate, passwordEncoder);
    }

    @Test
    void rejectsShortBootstrapAdminPassword() {
        BootstrapAdminProperties properties = new BootstrapAdminProperties(
                "admin",
                "admin@example.test",
                "short"
        );

        assertThatThrownBy(() -> service.bootstrap(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Bootstrap admin password must contain at least 12 characters.");
        verifyNoInteractions(jdbcTemplate, passwordEncoder);
    }

    @Test
    void createsEnabledSuperAdminWithEncodedPasswordAndRoleGrant() {
        UUID roleId = UUID.fromString("00000000-0000-0000-0000-000000000701");
        when(jdbcTemplate.queryForList(anyString(), eq("super-admin"))).thenReturn(List.of(idRow(roleId)));
        when(jdbcTemplate.queryForList(anyString(), eq("admin"))).thenReturn(List.of());
        when(jdbcTemplate.queryForList(anyString(), eq("admin@example.test"))).thenReturn(List.of());
        when(passwordEncoder.encode("VeryStrongPassword123!")).thenReturn("{bcrypt}encoded");

        BootstrapAdminResult result = service.bootstrap(new BootstrapAdminProperties(
                " admin ",
                " admin@example.test ",
                "VeryStrongPassword123!"
        ));

        assertThat(result).isEqualTo(BootstrapAdminResult.CREATED);
        verify(passwordEncoder).encode("VeryStrongPassword123!");
        verify(jdbcTemplate).update(
                startsWith("INSERT INTO users"),
                any(UUID.class),
                eq("admin"),
                eq("admin@example.test"),
                eq("{bcrypt}encoded")
        );
        verify(jdbcTemplate).update(startsWith("INSERT INTO user_roles"), any(UUID.class), eq(roleId));
    }

    @Test
    void grantsSuperAdminRoleToExistingUserWithoutOverwritingPassword() {
        UUID roleId = UUID.fromString("00000000-0000-0000-0000-000000000701");
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000009001");
        when(jdbcTemplate.queryForList(anyString(), eq("super-admin"))).thenReturn(List.of(idRow(roleId)));
        when(jdbcTemplate.queryForList(anyString(), eq("admin"))).thenReturn(List.of(idRow(userId)));

        BootstrapAdminResult result = service.bootstrap(new BootstrapAdminProperties(
                "admin",
                "admin@example.test",
                "VeryStrongPassword123!"
        ));

        assertThat(result).isEqualTo(BootstrapAdminResult.EXISTING_USER_GRANTED);
        verify(passwordEncoder, never()).encode(anyString());
        verify(jdbcTemplate, never()).update(startsWith("INSERT INTO users"), any(), any(), any(), any());
        verify(jdbcTemplate).update(startsWith("INSERT INTO user_roles"), eq(userId), eq(roleId));
    }

    private static Map<String, Object> idRow(UUID id) {
        return Map.of("id", id);
    }
}
