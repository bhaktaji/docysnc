package com.docsync.webhook;

import com.docsync.webhook.exception.WebhookSignatureException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Verifies {@code X-Hub-Signature-256} using HMAC-SHA256 and a constant-time comparison.
 * An unconfigured or blank secret rejects every delivery.
 */
@Component
public class GitHubWebhookVerifier {

    private static final String PREFIX = "sha256=";
    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;

    public GitHubWebhookVerifier(@Value("${docsync.github.webhook-secret:}") String secret) {
        this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
    }

    public void verify(byte[] body, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.startsWith(PREFIX)) {
            throw new WebhookSignatureException();
        }
        if (secret.length == 0) {
            throw new WebhookSignatureException();
        }

        String provided = signatureHeader.substring(PREFIX.length());
        String expected = HexFormat.of().formatHex(hmac(body));
        boolean valid = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                provided.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
        if (!valid) {
            throw new WebhookSignatureException();
        }
    }

    private byte[] hmac(byte[] body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return mac.doFinal(body);
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to verify webhook signature", ex);
        }
    }
}
