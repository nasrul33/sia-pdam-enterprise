package id.pdam.sia.shared.security;

import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class ProductionSecurityValidator {
    private static final String DEVELOPMENT_WEBHOOK_SECRET = "dev-only-change-me";

    public void validate(ProductionSecurityProperties properties) {
        if (properties == null || !properties.enabled()) {
            throw new IllegalStateException("Production security must be explicitly enabled.");
        }
        String webhookSecret = require(properties.webhookSecret(), "Production webhook secret is required.");
        if (DEVELOPMENT_WEBHOOK_SECRET.equals(webhookSecret) || webhookSecret.length() < 32) {
            throw new IllegalStateException("Production webhook secret must not use the development default and must contain at least 32 characters.");
        }
        String issuer = require(properties.issuerUri(), "Production OIDC issuer is required.");
        URI issuerUri;
        try {
            issuerUri = URI.create(issuer);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Production OIDC issuer must be a valid HTTPS URI.", exception);
        }
        if (!"https".equalsIgnoreCase(issuerUri.getScheme()) || issuerUri.getHost() == null) {
            throw new IllegalStateException("Production OIDC issuer must be a valid HTTPS URI.");
        }
        require(properties.clientId(), "Production OIDC client id is required.");
    }

    private static String require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
        return value.trim();
    }
}
