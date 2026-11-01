package com.docsync.github;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubAppJwtGeneratorTest {

    private static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");

    @Test
    void generatesRs256JwtWithGitHubClaims() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        GitHubAppJwtGenerator generator = new GitHubAppJwtGenerator(
                "123456", pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()),
                Clock.fixed(NOW, ZoneOffset.UTC));

        SignedJWT jwt = SignedJWT.parse(generator.generate());
        JWTClaimsSet claims = jwt.getJWTClaimsSet();

        assertEquals(JWSAlgorithm.RS256, jwt.getHeader().getAlgorithm());
        assertEquals("123456", claims.getIssuer());
        assertEquals(NOW.minusSeconds(60), claims.getIssueTime().toInstant());
        assertEquals(NOW.plus(Duration.ofMinutes(9)), claims.getExpirationTime().toInstant());
        assertTrue(jwt.verify(new RSASSAVerifier((RSAPublicKey) keyPair.getPublic())));
    }

    @Test
    void acceptsPkcs1EncodedPrivateKey() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        byte[] pkcs8 = keyPair.getPrivate().getEncoded();
        byte[] pkcs1 = extractPkcs1(pkcs8);

        GitHubAppJwtGenerator generator = new GitHubAppJwtGenerator(
                "42", pem("RSA PRIVATE KEY", pkcs1), Clock.fixed(NOW, ZoneOffset.UTC));

        SignedJWT jwt = SignedJWT.parse(generator.generate());
        assertTrue(jwt.verify(new RSASSAVerifier((RSAPublicKey) keyPair.getPublic())));
    }

    @Test
    void rejectsMissingOrInvalidConfigurationWithoutLeakingKeyMaterial() {
        GitHubAppJwtGenerator missingAppId = new GitHubAppJwtGenerator(
                "", "not-a-key", Clock.fixed(NOW, ZoneOffset.UTC));
        assertThrows(com.docsync.common.exception.ExternalServiceException.class, missingAppId::generate);

        GitHubAppJwtGenerator invalidKey = new GitHubAppJwtGenerator(
                "42", "not-a-key", Clock.fixed(NOW, ZoneOffset.UTC));
        IllegalStateException keyError = assertThrows(IllegalStateException.class, invalidKey::generate);
        assertFalse(keyError.getMessage().contains("not-a-key"));

        GitHubAppJwtGenerator missingKey = new GitHubAppJwtGenerator(
                "42", "", Clock.fixed(NOW, ZoneOffset.UTC));
        IllegalStateException error = assertThrows(IllegalStateException.class, missingKey::generate);
        assertFalse(error.getMessage().contains("PRIVATE KEY"));
    }

    private static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der)
                + "\n-----END " + type + "-----";
    }

    /**
     * PKCS#8 PrivateKeyInfo: SEQUENCE { version, algorithm, OCTET STRING { PKCS#1 } }.
     */
    private static byte[] extractPkcs1(byte[] pkcs8) {
        int offset = 0;
        if (pkcs8[offset] != 0x30) {
            throw new IllegalArgumentException("Expected PKCS#8 sequence");
        }
        offset++;
        if ((pkcs8[offset] & 0x80) != 0) {
            offset += (pkcs8[offset] & 0x7f) + 1;
        } else {
            offset++;
        }
        // version
        offset += 2 + pkcs8[offset + 1];
        // algorithm identifier
        offset += 2 + pkcs8[offset + 1];
        if (pkcs8[offset] != 0x04) {
            throw new IllegalArgumentException("Expected PKCS#1 octet string");
        }
        offset++;
        int length = pkcs8[offset] & 0xff;
        offset++;
        if ((length & 0x80) != 0) {
            int bytes = length & 0x7f;
            length = 0;
            for (int i = 0; i < bytes; i++) {
                length = (length << 8) | (pkcs8[offset++] & 0xff);
            }
        }
        byte[] pkcs1 = new byte[length];
        System.arraycopy(pkcs8, offset, pkcs1, 0, length);
        return pkcs1;
    }
}
