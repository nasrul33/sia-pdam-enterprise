package id.pdam.sia.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sia.security.production")
public record ProductionSecurityProperties(
        boolean enabled,
        String webhookSecret,
        String issuerUri,
        String clientId
) {
}
