package id.pdam.sia.admin.application;

import id.pdam.sia.shared.audit.AuditTrailService;
import id.pdam.sia.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserAdministrationServiceTest {
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final AuditTrailService auditTrailService = mock(AuditTrailService.class);
    private final IdentityProviderAdminPort identityProviderAdminPort = mock(IdentityProviderAdminPort.class);
    private final UserAdministrationService service = new UserAdministrationService(
            jdbcTemplate,
            auditTrailService,
            identityProviderAdminPort
    );

    @Test
    void userCannotDisableOwnAccount() {
        UUID adminId = UUID.randomUUID();
        when(jdbcTemplate.queryForList(anyString(), eq(adminId))).thenReturn(List.of(userRow(adminId, "admin", true)));

        assertThatThrownBy(() -> service.updateStatus(adminId, false, "admin", "self disable"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("USER_SELF_DISABLE_FORBIDDEN");

    }

    @Test
    void lastSuperAdminRoleCannotBeRemoved() {
        UUID lastAdminId = UUID.randomUUID();
        UUID financeRoleId = UUID.randomUUID();
        when(jdbcTemplate.queryForList(anyString(), eq(lastAdminId)))
                .thenReturn(List.of(userRow(lastAdminId, "last.admin", true)))
                .thenReturn(List.of(codeRow("super-admin")));
        when(jdbcTemplate.queryForList(anyString(), eq("other"))).thenReturn(List.of(codeRow("super-admin")));
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(Map.of("user_id", lastAdminId)));
        when(jdbcTemplate.queryForList(anyString(), eq("finance-staff")))
                .thenReturn(List.of(roleRow(financeRoleId, "finance-staff")));

        assertThatThrownBy(() -> service.replaceRoles(
                lastAdminId,
                Set.of("finance-staff"),
                "other",
                "change role"
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("LAST_SUPER_ADMIN_REQUIRED");

    }

    @Test
    void nonSuperAdminCannotEnableAccountThatHasSuperAdminRole() {
        UUID disabledAdminId = UUID.randomUUID();
        when(jdbcTemplate.queryForList(anyString(), eq(disabledAdminId)))
                .thenReturn(List.of(userRow(disabledAdminId, "disabled.admin", false)))
                .thenReturn(List.of(codeRow("super-admin")));
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq("admin.sistem"))).thenReturn(false);

        assertThatThrownBy(() -> service.updateStatus(
                disabledAdminId,
                true,
                "admin.sistem",
                "reactivate account"
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("SUPER_ADMIN_AUTHORITY_REQUIRED");
    }

    private static Map<String, Object> userRow(UUID id, String username, boolean enabled) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("username", username);
        row.put("email", username + "@example.test");
        row.put("enabled", enabled);
        row.put("updated_at", Instant.parse("2026-07-11T00:00:00Z"));
        return row;
    }

    private static Map<String, Object> codeRow(String code) {
        return Map.of("code", code);
    }

    private static Map<String, Object> roleRow(UUID id, String code) {
        return Map.of("id", id, "code", code);
    }
}
