package id.pdam.sia.admin.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserStatusRequest(
        boolean enabled,
        @NotBlank @Size(max = 500) String reason
) {
}
