package id.pdam.sia.payment.application;

import id.pdam.sia.payment.web.PaymentWebhookRequest;
import id.pdam.sia.shared.exception.BusinessException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class PaymentWebhookSignature {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String PREFIX = "sha256=";

    private PaymentWebhookSignature() {
    }

    public static String sign(String secret, PaymentWebhookRequest request) {
        String normalizedSecret = require(secret, "PAYMENT_WEBHOOK_SECRET_REQUIRED", "Payment webhook secret is required.");
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(normalizedSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return PREFIX + HexFormat.of().formatHex(mac.doFinal(canonicalPayload(request).getBytes(StandardCharsets.UTF_8)));
        } catch (InvalidKeyException | NoSuchAlgorithmException exception) {
            throw new BusinessException("PAYMENT_WEBHOOK_SIGNATURE_ERROR", "Payment webhook signature cannot be calculated.");
        }
    }

    public static boolean verify(String secret, PaymentWebhookRequest request, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        String expected = sign(secret, request);
        String normalizedSignature = signature.trim();
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                normalizedSignature.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String canonicalPayload(PaymentWebhookRequest request) {
        if (request == null) {
            throw new BusinessException("PAYMENT_WEBHOOK_REQUEST_REQUIRED", "Payment webhook request is required.");
        }
        return require(request.provider(), "PAYMENT_WEBHOOK_PROVIDER_REQUIRED", "Payment webhook provider is required.")
                + "\n" + require(request.externalReference(), "PAYMENT_WEBHOOK_REFERENCE_REQUIRED", "Payment webhook external reference is required.")
                + "\n" + require(request.idempotencyKey(), "PAYMENT_WEBHOOK_IDEMPOTENCY_REQUIRED", "Payment webhook idempotency key is required.")
                + "\n" + require(request.payload(), "PAYMENT_WEBHOOK_PAYLOAD_REQUIRED", "Payment webhook payload is required.");
    }

    private static String require(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }
}
