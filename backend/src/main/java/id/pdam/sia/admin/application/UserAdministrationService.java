package id.pdam.sia.admin.application;

import id.pdam.sia.shared.audit.AuditTrailEntry;
import id.pdam.sia.shared.audit.AuditTrailService;
import id.pdam.sia.shared.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Service
public class UserAdministrationService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final String SUPER_ADMIN = "super-admin";
    private static final String USER_SELECT = """
            SELECT id, username, email, enabled, updated_at
            FROM users
            """;
    private static final String USER_PAGE_SQL = USER_SELECT + """
            WHERE lower(username) LIKE ? ESCAPE '\\' OR lower(email) LIKE ? ESCAPE '\\'
            ORDER BY username
            LIMIT ? OFFSET ?
            """;
    private static final String USER_COUNT_SQL = """
            SELECT count(*)
            FROM users
            WHERE lower(username) LIKE ? ESCAPE '\\' OR lower(email) LIKE ? ESCAPE '\\'
            """;
    private static final String USER_BY_ID_SQL = USER_SELECT + "WHERE id = ?";
    private static final String USER_BY_ID_FOR_UPDATE_SQL = USER_SELECT + "WHERE id = ? FOR UPDATE";
    private static final String USER_ROLES_SQL = """
            SELECT r.code
            FROM user_roles ur
            JOIN roles r ON r.id = ur.role_id
            WHERE ur.user_id = ?
            ORDER BY r.code
            """;
    private static final String USER_AUTHORITIES_SQL = """
            SELECT authority
            FROM (
                SELECT p.code AS authority
                FROM user_roles ur
                JOIN role_permissions rp ON rp.role_id = ur.role_id
                JOIN permissions p ON p.id = rp.permission_id
                WHERE ur.user_id = ?
                UNION
                SELECT 'ROLE_' || upper(replace(r.code, '-', '_')) AS authority
                FROM user_roles ur
                JOIN roles r ON r.id = ur.role_id
                WHERE ur.user_id = ?
            ) resolved
            ORDER BY authority
            """;
    private static final String SUPER_ADMIN_USERS_FOR_UPDATE_SQL = """
            SELECT ur.user_id
            FROM user_roles ur
            JOIN roles r ON r.id = ur.role_id
            JOIN users u ON u.id = ur.user_id
            WHERE r.code = 'super-admin' AND u.enabled = true
            FOR UPDATE OF ur, u
            """;
    private static final String ACTOR_SUPER_ADMIN_SQL = """
            SELECT EXISTS (
                SELECT 1
                FROM users u
                JOIN user_roles ur ON ur.user_id = u.id
                JOIN roles r ON r.id = ur.role_id
                WHERE u.username = ? AND u.enabled = true AND r.code = 'super-admin'
            )
            """;

    private final JdbcTemplate jdbcTemplate;
    private final AuditTrailService auditTrailService;
    private final IdentityProviderAdminPort identityProviderAdminPort;

    public UserAdministrationService(
            JdbcTemplate jdbcTemplate,
            AuditTrailService auditTrailService,
            IdentityProviderAdminPort identityProviderAdminPort
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditTrailService = auditTrailService;
        this.identityProviderAdminPort = identityProviderAdminPort;
    }

    @Transactional(readOnly = true)
    public AdminUserPage listUsers(String search, int page, int size) {
        requirePage(page, size);
        String pattern = "%" + normalizeSearch(search) + "%";
        Long total = jdbcTemplate.queryForObject(USER_COUNT_SQL, Long.class, pattern, pattern);
        List<AdminUser> users = jdbcTemplate.queryForList(
                        USER_PAGE_SQL,
                        pattern,
                        pattern,
                        Math.min(size, MAX_PAGE_SIZE),
                        Math.multiplyExact((long) page, Math.min(size, MAX_PAGE_SIZE))
                ).stream()
                .map(this::hydrateUser)
                .toList();
        long totalItems = total == null ? 0 : total;
        int boundedSize = Math.min(size, MAX_PAGE_SIZE);
        int totalPages = totalItems == 0 ? 0 : (int) ((totalItems + boundedSize - 1) / boundedSize);
        return new AdminUserPage(users, page, boundedSize, totalItems, totalPages);
    }

    @Transactional(readOnly = true)
    public List<AdminRole> listRoles() {
        return jdbcTemplate.queryForList("SELECT id, code, name FROM roles ORDER BY name").stream()
                .map(row -> {
                    UUID roleId = uuid(row, "id");
                    List<String> permissions = jdbcTemplate.queryForList("""
                                    SELECT p.code
                                    FROM role_permissions rp
                                    JOIN permissions p ON p.id = rp.permission_id
                                    WHERE rp.role_id = ?
                                    ORDER BY p.code
                                    """, roleId).stream()
                            .map(permission -> text(permission, "code"))
                            .toList();
                    return new AdminRole(roleId, text(row, "code"), text(row, "name"), permissions);
                })
                .toList();
    }

    @Transactional
    public AdminUser updateStatus(UUID userId, boolean enabled, String actor, String reason) {
        String normalizedActor = requireText(actor, "ADMIN_ACTOR_REQUIRED", "Admin actor is required.");
        String normalizedReason = requireText(reason, "ADMIN_REASON_REQUIRED", "Audit reason is required.");
        Map<String, Object> target = lockUser(userId);
        String username = text(target, "username");
        boolean currentEnabled = bool(target, "enabled");
        if (!enabled && username.equalsIgnoreCase(normalizedActor)) {
            throw new BusinessException("USER_SELF_DISABLE_FORBIDDEN", "A user cannot disable their own account.");
        }
        if (currentEnabled == enabled) {
            return hydrateUser(target);
        }

        Set<String> currentRoles = new LinkedHashSet<>(loadRoleCodes(userId));
        if (currentRoles.contains(SUPER_ADMIN)) {
            if (!enabled) {
                ensureAnotherEnabledSuperAdmin(userId);
            }
            requireSuperAdminActor(normalizedActor);
        }

        jdbcTemplate.update(
                "UPDATE users SET enabled = ?, updated_at = now(), version = version + 1 WHERE id = ?",
                enabled,
                userId
        );
        identityProviderAdminPort.updateEnabled(username, enabled);
        auditTrailService.record(new AuditTrailEntry(
                normalizedActor,
                "ADMIN_USER",
                "UPDATE_USER_STATUS",
                userId.toString(),
                normalizedReason,
                "enabled=" + currentEnabled,
                "enabled=" + enabled,
                null,
                null,
                null
        ));
        return findUser(userId);
    }

    @Transactional
    public AdminUser replaceRoles(UUID userId, Set<String> roleCodes, String actor, String reason) {
        String normalizedActor = requireText(actor, "ADMIN_ACTOR_REQUIRED", "Admin actor is required.");
        String normalizedReason = requireText(reason, "ADMIN_REASON_REQUIRED", "Audit reason is required.");
        Set<String> requestedRoles = normalizeRoleCodes(roleCodes);
        Map<String, Object> target = lockUser(userId);
        String username = text(target, "username");
        Set<String> currentRoles = new LinkedHashSet<>(loadRoleCodes(userId));
        Map<String, UUID> resolvedRoles = resolveRoles(requestedRoles);

        if (currentRoles.contains(SUPER_ADMIN) && !requestedRoles.contains(SUPER_ADMIN)) {
            ensureAnotherEnabledSuperAdmin(userId);
        }
        if (currentRoles.contains(SUPER_ADMIN) || requestedRoles.contains(SUPER_ADMIN)) {
            requireSuperAdminActor(normalizedActor);
        }

        jdbcTemplate.update("DELETE FROM user_roles WHERE user_id = ?", userId);
        resolvedRoles.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> jdbcTemplate.update(
                        "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)",
                        userId,
                        entry.getValue()
                ));
        jdbcTemplate.update("UPDATE users SET updated_at = now(), version = version + 1 WHERE id = ?", userId);
        identityProviderAdminPort.replaceRoles(username, requestedRoles);
        auditTrailService.record(new AuditTrailEntry(
                normalizedActor,
                "ADMIN_USER",
                "REPLACE_USER_ROLES",
                userId.toString(),
                normalizedReason,
                currentRoles.toString(),
                requestedRoles.toString(),
                null,
                null,
                null
        ));
        return findUser(userId);
    }

    private AdminUser findUser(UUID userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(USER_BY_ID_SQL, userId);
        if (rows.isEmpty()) {
            throw new BusinessException("ADMIN_USER_NOT_FOUND", "User was not found.");
        }
        return hydrateUser(rows.getFirst());
    }

    private Map<String, Object> lockUser(UUID userId) {
        if (userId == null) {
            throw new BusinessException("ADMIN_USER_ID_REQUIRED", "User id is required.");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(USER_BY_ID_FOR_UPDATE_SQL, userId);
        if (rows.isEmpty()) {
            throw new BusinessException("ADMIN_USER_NOT_FOUND", "User was not found.");
        }
        return rows.getFirst();
    }

    private AdminUser hydrateUser(Map<String, Object> row) {
        UUID id = uuid(row, "id");
        String username = text(row, "username");
        return new AdminUser(
                id,
                username,
                text(row, "email"),
                bool(row, "enabled"),
                loadRoleCodes(id),
                loadAuthorities(id),
                identityProviderAdminPort.status(id, username),
                instant(row, "updated_at")
        );
    }

    private List<String> loadRoleCodes(UUID userId) {
        return jdbcTemplate.queryForList(USER_ROLES_SQL, userId).stream()
                .map(row -> text(row, "code"))
                .toList();
    }

    private List<String> loadAuthorities(UUID userId) {
        return jdbcTemplate.queryForList(USER_AUTHORITIES_SQL, userId, userId).stream()
                .map(row -> text(row, "authority"))
                .toList();
    }

    private Map<String, UUID> resolveRoles(Set<String> roleCodes) {
        String placeholders = String.join(",", java.util.Collections.nCopies(roleCodes.size(), "?"));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, code FROM roles WHERE code IN (" + placeholders + ") ORDER BY code",
                roleCodes.toArray()
        );
        Map<String, UUID> resolved = new LinkedHashMap<>();
        rows.forEach(row -> resolved.put(text(row, "code"), uuid(row, "id")));
        if (!resolved.keySet().equals(roleCodes)) {
            Set<String> missing = new LinkedHashSet<>(roleCodes);
            missing.removeAll(resolved.keySet());
            throw new BusinessException("ADMIN_ROLE_NOT_FOUND", "Unknown roles: " + String.join(", ", missing));
        }
        return resolved;
    }

    private void ensureAnotherEnabledSuperAdmin(UUID targetUserId) {
        List<Map<String, Object>> superAdmins = jdbcTemplate.queryForList(SUPER_ADMIN_USERS_FOR_UPDATE_SQL);
        boolean anotherAdminExists = superAdmins.stream()
                .map(row -> uuid(row, "user_id"))
                .anyMatch(id -> !id.equals(targetUserId));
        if (!anotherAdminExists) {
            throw new BusinessException("LAST_SUPER_ADMIN_REQUIRED", "At least one enabled super-admin must remain.");
        }
    }

    private void requireSuperAdminActor(String actor) {
        Boolean superAdmin = jdbcTemplate.queryForObject(ACTOR_SUPER_ADMIN_SQL, Boolean.class, actor);
        if (!Boolean.TRUE.equals(superAdmin)) {
            throw new BusinessException(
                    "SUPER_ADMIN_AUTHORITY_REQUIRED",
                    "Only an enabled super-admin may change super-admin access."
            );
        }
    }

    private static Set<String> normalizeRoleCodes(Collection<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            throw new BusinessException("ADMIN_USER_ROLE_REQUIRED", "At least one role is required.");
        }
        if (roleCodes.size() > 20) {
            throw new BusinessException("ADMIN_USER_ROLE_LIMIT", "A user may have at most 20 roles.");
        }
        Set<String> normalized = new TreeSet<>();
        for (String roleCode : roleCodes) {
            String value = requireText(roleCode, "ADMIN_USER_ROLE_REQUIRED", "Role code is required.")
                    .toLowerCase(Locale.ROOT);
            if (!value.matches("[a-z0-9][a-z0-9-]{1,63}")) {
                throw new BusinessException("ADMIN_USER_ROLE_INVALID", "Role code is invalid.");
            }
            normalized.add(value);
        }
        return normalized;
    }

    private static String normalizeSearch(String search) {
        String normalized = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 128) {
            throw new BusinessException("ADMIN_USER_SEARCH_TOO_LONG", "User search may contain at most 128 characters.");
        }
        return normalized
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private static void requirePage(int page, int size) {
        if (page < 0) {
            throw new BusinessException("PAGE_INVALID", "Page must be zero or greater.");
        }
        if (size < 1) {
            throw new BusinessException("PAGE_SIZE_INVALID", "Page size must be at least one.");
        }
    }

    private static UUID uuid(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(String.valueOf(value));
    }

    private static String text(Map<String, Object> row, String column) {
        return requireText(String.valueOf(row.get(column)), "ADMIN_DATA_INVALID", "Admin user data is invalid.");
    }

    private static boolean bool(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value instanceof Boolean booleanValue ? booleanValue : Boolean.parseBoolean(String.valueOf(value));
    }

    private static Instant instant(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        return Instant.parse(String.valueOf(value));
    }

    private static String requireText(String value, String code, String message) {
        if (value == null || value.isBlank() || "null".equals(value)) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    public record AdminUser(
            UUID id,
            String username,
            String email,
            boolean enabled,
            List<String> roles,
            List<String> authorities,
            IdentityProviderAdminPort.IdentityProviderStatus identityProviderStatus,
            Instant updatedAt
    ) {
        public AdminUser {
            roles = List.copyOf(roles);
            authorities = List.copyOf(authorities);
        }
    }

    public record AdminRole(UUID id, String code, String name, List<String> permissions) {
        public AdminRole {
            permissions = List.copyOf(permissions);
        }
    }

    public record AdminUserPage(List<AdminUser> items, int page, int size, long totalItems, int totalPages) {
        public AdminUserPage {
            items = List.copyOf(items);
        }
    }
}
