package id.pdam.sia.shared.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private static final String USER_WAS_NOT_FOUND = "User was not found.";
    private static final String USER_SQL = """
            SELECT username, password_hash, enabled
            FROM users
            WHERE username = ?
            """;
    private static final String AUTHORITY_SQL = """
            SELECT authority
            FROM (
                SELECT p.code AS authority
                FROM users u
                JOIN user_roles ur ON ur.user_id = u.id
                JOIN role_permissions rp ON rp.role_id = ur.role_id
                JOIN permissions p ON p.id = rp.permission_id
                WHERE u.username = ?
                UNION
                SELECT 'ROLE_' || upper(replace(r.code, '-', '_')) AS authority
                FROM users u
                JOIN user_roles ur ON ur.user_id = u.id
                JOIN roles r ON r.id = ur.role_id
                WHERE u.username = ?
            ) authorities
            ORDER BY authority
            """;

    private final JdbcTemplate jdbcTemplate;

    public DatabaseUserDetailsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalizedUsername = normalizeUsername(username);
        Map<String, Object> userRow = findUserRow(normalizedUsername);

        String loadedUsername = requireText(userRow, "username");
        String passwordHash = requireText(userRow, "password_hash");
        boolean enabled = toBoolean(userRow.get("enabled"));

        return User.withUsername(loadedUsername)
                .password(passwordHash)
                .disabled(!enabled)
                .authorities(loadAuthorities(loadedUsername))
                .build();
    }

    private Map<String, Object> findUserRow(String username) {
        List<Map<String, Object>> users = jdbcTemplate.queryForList(USER_SQL, username);
        if (users.isEmpty()) {
            throw notFound();
        }
        return users.getFirst();
    }

    private List<GrantedAuthority> loadAuthorities(String username) {
        return jdbcTemplate.queryForList(AUTHORITY_SQL, username, username)
                .stream()
                .map(row -> row.get("authority"))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(String::trim)
                .filter(authority -> !authority.isBlank())
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    private static String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw notFound();
        }
        return username.trim();
    }

    private static String requireText(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value == null || value.toString().isBlank()) {
            throw notFound();
        }
        return value.toString();
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue() != 0;
        }
        if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        return false;
    }

    private static UsernameNotFoundException notFound() {
        return new UsernameNotFoundException(USER_WAS_NOT_FOUND);
    }
}
