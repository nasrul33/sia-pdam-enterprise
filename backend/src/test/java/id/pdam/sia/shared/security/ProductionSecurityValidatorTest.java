package id.pdam.sia.shared.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSecurityValidatorTest {
    private final ProductionSecurityValidator validator = new ProductionSecurityValidator();

    @Test
    void productionRejectsDefaultWebhookSecret() {
        ProductionSecurityProperties properties = new ProductionSecurityProperties(
                true,
                "dev-only-change-me",
                "https://identity.example.test/realms/sia-pdam",
                "sia-pdam"
        );

        assertThatThrownBy(() -> validator.validate(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("webhook secret");
    }

    @Test
    void productionRejectsIncompleteOidcConfiguration() {
        ProductionSecurityProperties properties = new ProductionSecurityProperties(
                true,
                "production-webhook-secret-with-32-characters",
                "",
                "sia-pdam"
        );

        assertThatThrownBy(() -> validator.validate(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("issuer");
    }

    @Test
    void validProductionConfigurationPasses() {
        ProductionSecurityProperties properties = new ProductionSecurityProperties(
                true,
                "production-webhook-secret-with-32-characters",
                "https://identity.example.test/realms/sia-pdam",
                "sia-pdam"
        );

        assertThatCode(() -> validator.validate(properties)).doesNotThrowAnyException();
    }
}
