package id.pdam.sia.shared.security;

import id.pdam.sia.auth.AuthController;
import id.pdam.sia.payment.application.PaymentWebhookApplicationService;
import id.pdam.sia.payment.domain.PaymentWebhookEvent;
import id.pdam.sia.payment.web.PaymentWebhookController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({
        AuthController.class,
        PaymentWebhookController.class
})
@Import(SecurityConfig.class)
@ImportAutoConfiguration({
        SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class
})
class SecurityConfigTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentWebhookApplicationService paymentWebhookApplicationService;

    @Test
    void authMeIsPublicAndReturnsAnonymousStateWithoutBasicAuth() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").doesNotExist())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.authorities").isArray())
                .andExpect(jsonPath("$.authorities").isEmpty());
    }

    @Test
    void providerWebhookPostIsPublicAtFilterChainAndValidatedByApplicationService() throws Exception {
        when(paymentWebhookApplicationService.receiveWebhook(any(), eq("sha256=test"), eq("payment-webhook")))
                .thenReturn(new PaymentWebhookEvent("MIDTRANS", "T092-SEC", "idem-t092-sec", "{}"));

        mockMvc.perform(post("/api/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Payment-Signature", "sha256=test")
                        .content("""
                                {
                                  "provider": "MIDTRANS",
                                  "externalReference": "T092-SEC",
                                  "idempotencyKey": "idem-t092-sec",
                                  "payload": "{}"
                                }
                                """))
                .andExpect(status().isAccepted());

        verify(paymentWebhookApplicationService)
                .receiveWebhook(any(), eq("sha256=test"), eq("payment-webhook"));
    }
}
