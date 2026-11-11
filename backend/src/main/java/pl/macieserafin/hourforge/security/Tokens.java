package pl.macieserafin.hourforge.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** One-time tokens for links and OAuth states: made from 32 random bytes and stored only as a SHA-256 hash. */
public final class Tokens {
    private static final SecureRandom RANDOM = new SecureRandom();

    private Tokens() {
    }

    /** 32 random bytes as URL-safe Base64 without padding: 43 characters that fit in an address. */
    public static String random() {
        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** The hex SHA-256 of a token, which is what the database keeps instead of the token. */
    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
