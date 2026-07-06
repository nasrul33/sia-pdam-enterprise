package id.pdam.sia.auth;

import java.util.List;

public record CurrentUserResponse(
        String username,
        boolean authenticated,
        List<String> authorities
) {
}
