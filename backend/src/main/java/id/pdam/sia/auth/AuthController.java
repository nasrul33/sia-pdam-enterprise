package id.pdam.sia.auth;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public CurrentUserResponse currentUser(Authentication authentication) {
        return new CurrentUserResponse(
                authentication.getName(),
                authentication.isAuthenticated(),
                authorities(authentication)
        );
    }

    private static List<String> authorities(Authentication authentication) {
        return authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority != null && !authority.isBlank())
                .distinct()
                .sorted()
                .toList();
    }
}
