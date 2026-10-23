package pl.macieserafin.hourforge.twofactor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * Time-based one-time codes (RFC 6238 over RFC 4226): HMAC-SHA1, 30-second steps, 6 digits, the settings every
 * authenticator app uses by default. A code is accepted one step early or late to allow for clock drift.
 */
public final class Totp {
    static final int STEP_SECONDS = 30;
    static final int DIGITS = 6;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    /** A new 160-bit secret, Base32-encoded as authenticator apps expect. */
    public static String newSecret() {
        var bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return base32(bytes);
    }

    public static long step(Instant at) {
        return Math.floorDiv(at.getEpochSecond(), STEP_SECONDS);
    }

    /** The code for one time step, zero-padded to six digits. */
    public static String code(String secret, long step) {
        try {
            var mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(unbase32(secret), "HmacSHA1"));
            var hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format(Locale.ROOT, "%0" + DIGITS + "d", binary % 1_000_000);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA1 is not available", exception);
        }
    }

    /**
     * The step a code belongs to, if it is valid at {@code now} (±1 step) and newer than {@code lastUsedStep}, so the
     * same code never works twice.
     */
    public static OptionalLong verify(String secret, String code, Instant now, Long lastUsedStep) {
        if (code == null || !code.matches("\\d{6}")) return OptionalLong.empty();
        long current = step(now);
        for (long step = current - 1; step <= current + 1; step++) {
            if (lastUsedStep != null && step <= lastUsedStep) continue;
            if (java.security.MessageDigest.isEqual(code(secret, step).getBytes(), code.getBytes())) return OptionalLong.of(step);
        }
        return OptionalLong.empty();
    }

    /** The link an authenticator app reads from the QR code. */
    public static String uri(String issuer, String account, String secret) {
        var label = encode(issuer) + ":" + encode(account);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + encode(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    static String base32(byte[] bytes) {
        var out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) out.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        return out.toString();
    }

    static byte[] unbase32(String text) {
        var out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char character : text.toUpperCase(Locale.ROOT).toCharArray()) {
            if (character == '=' || character == ' ') continue;
            int value = BASE32.indexOf(character);
            if (value < 0) throw new IllegalArgumentException("Not Base32");
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }
}
