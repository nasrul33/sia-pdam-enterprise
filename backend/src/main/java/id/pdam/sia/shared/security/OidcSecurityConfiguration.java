package id.pdam.sia.shared.security;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile({"prod", "oidc-smoke"})
public class OidcSecurityConfiguration {
    @Bean
    KeycloakAuthorityConverter keycloakAuthorityConverter(ProductionSecurityProperties properties) {
        return new KeycloakAuthorityConverter(properties.clientId());
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter(KeycloakAuthorityConverter authorityConverter) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorityConverter);
        converter.setPrincipalClaimName("preferred_username");
        return converter;
    }

    @Bean
    SecurityFilterChain oidcSecurityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter authenticationConverter
    ) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/dashboard/overview").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/payments/webhook").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(authenticationConverter)
                ))
                .build();
    }

    @Bean
    @Profile("prod")
    ApplicationRunner productionSecurityValidation(
            ProductionSecurityProperties properties,
            ProductionSecurityValidator validator
    ) {
        return args -> validator.validate(properties);
    }
}
