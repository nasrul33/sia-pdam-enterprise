package id.pdam.sia.admin.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateUserRolesRequest(
        @NotEmpty @Size(max = 20) Set<@Size(min = 2, max = 64) String> roleCodes,
        @NotBlank @Size(max = 500) String reason
) {
}
