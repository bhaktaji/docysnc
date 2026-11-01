package com.docsync.webhook;

import com.docsync.webhook.exception.WebhookSignatureException;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitHubWebhookVerifierTest {

    private static final String SECRET = "test-webhook-secret";

    @Test
    void acceptsValidSignature() {
        byte[] body = "{\"action\":\"created\"}".getBytes(StandardCharsets.UTF_8);
        GitHubWebhookVerifier verifier = new GitHubWebhookVerifier(SECRET);

        assertDoesNotThrow(() -> verifier.verify(body, "sha256=" + hmac(body)));
    }

    @Test
    void rejectsMissingOrMalformedSignature() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        GitHubWebhookVerifier verifier = new GitHubWebhookVerifier(SECRET);

        assertThrows(WebhookSignatureException.class, () -> verifier.verify(body, null));
        assertThrows(WebhookSignatureException.class, () -> verifier.verify(body, "deadbeef"));
        assertThrows(WebhookSignatureException.class, () -> verifier.verify(body, "sha1=deadbeef"));
    }

    @Test
    void rejectsTamperedBody() {
        byte[] original = "{\"action\":\"created\"}".getBytes(StandardCharsets.UTF_8);
        byte[] tampered = "{\"action\":\"deleted\"}".getBytes(StandardCharsets.UTF_8);
        GitHubWebhookVerifier verifier = new GitHubWebhookVerifier(SECRET);

        assertThrows(WebhookSignatureException.class,
                () -> verifier.verify(tampered, "sha256=" + hmac(original)));
    }

    @Test
    void rejectsEverythingWhenSecretIsNotConfigured() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        GitHubWebhookVerifier verifier = new GitHubWebhookVerifier("");

        assertThrows(WebhookSignatureException.class,
                () -> verifier.verify(body, "sha256=" + hmac(body)));
    }

    private static String hmac(byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
