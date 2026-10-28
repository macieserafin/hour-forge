package pl.macieserafin.hourforge.auth;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.user.User;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;

/** Creates and redeems one-time e-mail links. Only a SHA-256 hash of each token is stored. */
@Service
public class EmailTokenService {
    private final EmailTokenRepository tokens;
    private final UserCredentialRepository credentials;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public EmailTokenService(EmailTokenRepository tokens, UserCredentialRepository credentials, Clock clock) {
        this.tokens = tokens;
        this.credentials = credentials;
        this.clock = clock;
    }

    /** Issues a new token and invalidates the user's earlier unused tokens of that purpose. */
    @Transactional
    public String issue(User user, EmailTokenPurpose purpose, String newEmail) {
        var now = clock.instant();
        // Entities, not a bulk update, so tokens already loaded in this transaction stay consistent.
        tokens.findByUserIdAndPurposeAndUsedAtIsNull(user.getId(), purpose).forEach(old -> old.markUsed(now));
        var bytes = new byte[32];
        random.nextBytes(bytes);
        var raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var token = new EmailToken(user, purpose, hash(raw), newEmail, now.plus(purpose.validity()));
        // Reset and address-change links die with the credentials they were issued for.
        if (purpose.boundToCredentials()) {
            credentials.findSessionVersionForEmail(user.getId(), user.getEmail()).ifPresent(token::bindToCredentialVersion);
        }
        tokens.saveAndFlush(token);
        return raw;
    }

    /** Marks the token used and returns it, or rejects unknown, used, expired and wrong-purpose tokens alike. */
    @Transactional
    public EmailToken redeem(String raw, EmailTokenPurpose purpose) {
        if (raw == null || raw.isBlank() || raw.length() > 128) throw invalid();
        var token = tokens.findByTokenHash(hash(raw.strip())).orElseThrow(EmailTokenService::invalid);
        var now = clock.instant();
        if (token.getPurpose() != purpose || token.getUsedAt() != null || !token.getExpiresAt().isAfter(now)) {
            throw invalid();
        }
        if (purpose.boundToCredentials() && !issuedForCurrentCredentials(token)) throw invalid();
        token.markUsed(now);
        tokens.flush();
        return token;
    }

    /** Locks the credential row, so a concurrent password change cannot slip in between this check and the redemption. */
    private boolean issuedForCurrentCredentials(EmailToken token) {
        var issuedFor = token.getCredentialVersion();
        return issuedFor != null && credentials.findLockedByUserId(token.getUser().getId())
                .map(credential -> credential.getSessionVersion() == issuedFor).orElse(false);
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "The link is invalid or has expired");
    }

    private static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
