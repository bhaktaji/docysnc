package com.docsync.github;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads a GitHub App private key from a PEM string.
 *
 * <p>Both PKCS#8 ({@code BEGIN PRIVATE KEY}) and PKCS#1 ({@code BEGIN RSA PRIVATE KEY})
 * encodings are accepted. PKCS#1 keys are wrapped in a PKCS#8 PrivateKeyInfo structure
 * so that the JDK's RSA KeyFactory can read them without extra dependencies.</p>
 *
 * <p>Errors never contain key material.</p>
 */
public final class PemPrivateKeyLoader {

    private static final Pattern PEM_PATTERN = Pattern.compile(
            "-----BEGIN (RSA )?PRIVATE KEY-----([\\s\\S]*?)-----END (?:RSA )?PRIVATE KEY-----");

    private static final byte[] ALGORITHM_IDENTIFIER = {
            0x30, 0x0d,
            0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01,
            0x05, 0x00
    };

    private PemPrivateKeyLoader() {
    }

    public static PrivateKey load(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalStateException("GitHub App private key is not configured");
        }

        Matcher matcher = PEM_PATTERN.matcher(pem.replace("\\n", "\n"));
        if (!matcher.find()) {
            throw new IllegalStateException("GitHub App private key is not a valid PEM block");
        }

        boolean pkcs1 = matcher.group(1) != null;
        byte[] decoded;
        try {
            decoded = Base64.getMimeDecoder().decode(matcher.group(2));
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("GitHub App private key contains invalid base64", ex);
        }

        byte[] pkcs8 = pkcs1 ? wrapPkcs1(decoded) : decoded;
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("GitHub App private key could not be parsed", ex);
        }
    }

    /**
     * Builds {@code PrivateKeyInfo ::= SEQUENCE { version, algorithm, OCTET STRING }}.
     */
    private static byte[] wrapPkcs1(byte[] pkcs1) {
        byte[] version = {0x02, 0x01, 0x00};
        byte[] octetString = der(0x04, pkcs1);
        byte[] body = concat(version, ALGORITHM_IDENTIFIER, octetString);
        return der(0x30, body);
    }

    private static byte[] der(int tag, byte[] content) {
        byte[] length = derLength(content.length);
        byte[] out = new byte[1 + length.length + content.length];
        out[0] = (byte) tag;
        System.arraycopy(length, 0, out, 1, length.length);
        System.arraycopy(content, 0, out, 1 + length.length, content.length);
        return out;
    }

    private static byte[] derLength(int length) {
        if (length < 0x80) {
            return new byte[]{(byte) length};
        }
        int bytes = 0;
        int value = length;
        while (value > 0) {
            bytes++;
            value >>>= 8;
        }
        byte[] encoded = new byte[bytes + 1];
        encoded[0] = (byte) (0x80 | bytes);
        for (int i = bytes; i > 0; i--) {
            encoded[i] = (byte) (length & 0xff);
            length >>>= 8;
        }
        return encoded;
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] part : parts) {
            total += part.length;
        }
        byte[] out = new byte[total];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, offset, part.length);
            offset += part.length;
        }
        return out;
    }
}
