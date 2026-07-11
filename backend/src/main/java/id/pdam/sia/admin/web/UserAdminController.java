package id.pdam.sia.admin.web;

import id.pdam.sia.admin.application.UserAdministrationService;
import id.pdam.sia.shared.security.Permissions;
import id.pdam.sia.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/admin")
public class UserAdminController {
    private final UserAdministrationService service;

    public UserAdminController(UserAdministrationService service) {
        this.service = service;
    }

    @GetMapping("/users")
    @PreAuthorize(Permissions.USER_READ)
    public PageResponse<UserAdminResponse> listUsers(
            @RequestParam(defaultValue = "") @Size(max = 128) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        UserAdministrationService.AdminUserPage result = service.listUsers(search, page, size);
        return new PageResponse<>(
                result.items().stream().map(UserAdminResponse::from).toList(),
                result.page(),
                result.size(),
                result.totalItems(),
                result.totalPages()
        );
    }

    @GetMapping("/roles")
    @PreAuthorize(Permissions.USER_READ)
    public List<RoleResponse> listRoles() {
        return service.listRoles().stream().map(RoleResponse::from).toList();
    }

    @PatchMapping("/users/{userId}/status")
    @PreAuthorize(Permissions.USER_MANAGE)
    public UserAdminResponse updateStatus(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserStatusRequest request,
            Principal principal
    ) {
        return UserAdminResponse.from(service.updateStatus(
                userId,
                request.enabled(),
                actor(principal),
                request.reason()
        ));
    }

    @PutMapping("/users/{userId}/roles")
    @PreAuthorize(Permissions.ROLE_MANAGE)
    public UserAdminResponse replaceRoles(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRolesRequest request,
            Principal principal
    ) {
        return UserAdminResponse.from(service.replaceRoles(
                userId,
                request.roleCodes(),
                actor(principal),
                request.reason()
        ));
    }

    private static String actor(Principal principal) {
        return principal == null ? "system" : principal.getName();
    }
}
