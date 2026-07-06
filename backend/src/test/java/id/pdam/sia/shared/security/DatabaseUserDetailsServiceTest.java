package id.pdam.sia.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseUserDetailsServiceTest {
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final DatabaseUserDetailsService service = new DatabaseUserDetailsService(jdbcTemplate);

    @Test
    void loadsEnabledUserWithPermissionAndRoleAuthorities() {
        when(jdbcTemplate.queryForList(anyString(), eq("piutang.admin")))
                .thenReturn(List.of(userRow("piutang.admin", "{noop}secret", true)));
        when(jdbcTemplate.queryForList(anyString(), eq("piutang.admin"), eq("piutang.admin")))
                .thenReturn(List.of(
                        authorityRow("ROLE_PETUGAS_PIUTANG"),
                        authorityRow("collection-action.create"),
                        authorityRow("collection-action.read")
                ));

        var userDetails = service.loadUserByUsername("  piutang.admin  ");

        assertThat(userDetails.getUsername()).isEqualTo("piutang.admin");
        assertThat(userDetails.getPassword()).isEqualTo("{noop}secret");
        assertThat(userDetails.isEnabled()).isTrue();
        assertThat(userDetails.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly(
                        "ROLE_PETUGAS_PIUTANG",
                        "collection-action.create",
                        "collection-action.read"
                );
    }

    @Test
    void rejectsUnknownUser() {
        when(jdbcTemplate.queryForList(anyString(), eq("missing.user"))).thenReturn(List.of());

        assertThatThrownBy(() -> service.loadUserByUsername("missing.user"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("User was not found.");
    }

    @Test
    void loadsDisabledUserAsDisabled() {
        when(jdbcTemplate.queryForList(anyString(), eq("locked.user")))
                .thenReturn(List.of(userRow("locked.user", "{noop}secret", false)));
        when(jdbcTemplate.queryForList(anyString(), eq("locked.user"), eq("locked.user")))
                .thenReturn(List.of());

        var userDetails = service.loadUserByUsername("locked.user");

        assertThat(userDetails.isEnabled()).isFalse();
        assertThat(userDetails.getAuthorities()).isEmpty();
    }

    @Test
    void rejectsBlankUsername() {
        assertThatThrownBy(() -> service.loadUserByUsername("   "))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("User was not found.");
    }

    private static Map<String, Object> userRow(String username, String passwordHash, boolean enabled) {
        return Map.of(
                "username", username,
                "password_hash", passwordHash,
                "enabled", enabled
        );
    }

    private static Map<String, Object> authorityRow(String authority) {
        return Map.of("authority", authority);
    }
}
