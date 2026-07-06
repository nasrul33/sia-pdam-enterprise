package id.pdam.sia.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sia.security.bootstrap-admin")
public record BootstrapAdminProperties(
        String username,
        String email,
        String password
) {
    public BootstrapAdminProperties {
        username = trimmedOrEmpty(username);
        email = trimmedOrEmpty(email);
        password = password == null ? "" : password;
    }

    boolean isEmpty() {
        return username.isBlank() && email.isBlank() && password.isBlank();
    }

    boolean isComplete() {
        return !username.isBlank() && !email.isBlank() && !password.isBlank();
    }

    private static String trimmedOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
