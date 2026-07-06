package id.pdam.sia.shared.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class BootstrapAdminUserService {
    private static final String SUPER_ADMIN_ROLE_CODE = "super-admin";
    private static final int MIN_PASSWORD_LENGTH = 12;
    private static final String FIND_ROLE_ID_SQL = """
            SELECT id
            FROM roles
            WHERE code = ?
            """;
    private static final String FIND_USER_ID_BY_USERNAME_SQL = """
            SELECT id
            FROM users
            WHERE username = ?
            """;
    private static final String FIND_USER_ID_BY_EMAIL_SQL = """
            SELECT id
            FROM users
            WHERE email = ?
            """;
    private static final String INSERT_USER_SQL = """
            INSERT INTO users (id, username, email, password_hash, enabled)
            VALUES (?, ?, ?, ?, true)
            """;
    private static final String GRANT_ROLE_SQL = """
            INSERT INTO user_roles (user_id, role_id)
            VALUES (?, ?)
            ON CONFLICT DO NOTHING
            """;

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAdminUserService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public BootstrapAdminResult bootstrap(BootstrapAdminProperties properties) {
        BootstrapAdminProperties bootstrapProperties = properties == null
                ? new BootstrapAdminProperties("", "", "")
                : properties;

        validate(bootstrapProperties);
        if (bootstrapProperties.isEmpty()) {
            return BootstrapAdminResult.SKIPPED;
        }

        UUID roleId = findId(FIND_ROLE_ID_SQL, SUPER_ADMIN_ROLE_CODE)
                .orElseThrow(() -> new IllegalStateException("Required RBAC role was not seeded: super-admin"));

        Optional<UUID> existingUserId = findId(FIND_USER_ID_BY_USERNAME_SQL, bootstrapProperties.username());
        if (existingUserId.isPresent()) {
            grantRole(existingUserId.get(), roleId);
            return BootstrapAdminResult.EXISTING_USER_GRANTED;
        }

        if (findId(FIND_USER_ID_BY_EMAIL_SQL, bootstrapProperties.email()).isPresent()) {
            throw new IllegalStateException("Bootstrap admin email is already assigned to another user.");
        }

        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                INSERT_USER_SQL,
                userId,
                bootstrapProperties.username(),
                bootstrapProperties.email(),
                passwordEncoder.encode(bootstrapProperties.password())
        );
        grantRole(userId, roleId);
        return BootstrapAdminResult.CREATED;
    }

    private static void validate(BootstrapAdminProperties properties) {
        if (properties.isEmpty()) {
            return;
        }
        if (!properties.isComplete()) {
            throw new IllegalStateException(
                    "Bootstrap admin requires username, email, and password when any bootstrap value is provided."
            );
        }
        if (!properties.email().contains("@")) {
            throw new IllegalStateException("Bootstrap admin email must be valid.");
        }
        if (properties.password().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("Bootstrap admin password must contain at least 12 characters.");
        }
    }

    private Optional<UUID> findId(String sql, String value) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, value);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toUuid(rows.getFirst().get("id")));
    }

    private void grantRole(UUID userId, UUID roleId) {
        jdbcTemplate.update(GRANT_ROLE_SQL, userId, roleId);
    }

    private static UUID toUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(value.toString());
    }
}
