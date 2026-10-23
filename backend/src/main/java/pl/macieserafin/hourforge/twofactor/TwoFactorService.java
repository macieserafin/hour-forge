package pl.macieserafin.hourforge.twofactor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.auth.AccountMailService;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.jira.TokenCipher;
import pl.macieserafin.hourforge.security.PasswordChangeAttempts;
import pl.macieserafin.hourforge.security.RateLimitProperties;
import pl.macieserafin.hourforge.security.RateLimiter;
import pl.macieserafin.hourforge.security.TooManyRequestsException;
import pl.macieserafin.hourforge.user.UserRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Optional two-step sign-in with an authenticator app (TOTP) and one-time backup codes. Turning it on, off and
 * renewing the codes needs the current password; the person is told by e-mail every time.
 */
@Service
public class TwoFactorService {
    private static final Logger log = LoggerFactory.getLogger(TwoFactorService.class);
    static final String ISSUER = "HourForge";
    static final int BACKUP_CODES = 10;
    private static final String CODE_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";

    public record Status(boolean available, boolean enabled, long backupCodesLeft) {
    }

    /** Shown once while setting up: the secret for typing in and the link for the QR code. */
    public record Setup(String secret, String uri) {
    }

    public record BackupCodes(List<String> codes) {
    }

    private final SecureRandom random = new SecureRandom();
    private final TokenCipher cipher;
    private final UserTwoFactorRepository secrets;
    private final BackupCodeRepository backupCodes;
    private final UserRepository users;
    private final UserCredentialRepository credentials;
    private final PasswordEncoder encoder;
    private final PasswordChangeAttempts passwordAttempts;
    private final RateLimiter limiter;
    private final RateLimitProperties limits;
    private final AccountMailService mail;
    private final Clock clock;

    public TwoFactorService(@Value("${app.two-factor.encryption-key:}") String key,
                            @Value("${app.two-factor.encryption-key-version:v1}") String keyVersion,
                            UserTwoFactorRepository secrets, BackupCodeRepository backupCodes, UserRepository users,
                            UserCredentialRepository credentials, PasswordEncoder encoder, PasswordChangeAttempts passwordAttempts,
                            RateLimiter limiter, RateLimitProperties limits, AccountMailService mail, Clock clock) {
        this.cipher = cipher(key, keyVersion);
        this.secrets = secrets;
        this.backupCodes = backupCodes;
        this.users = users;
        this.credentials = credentials;
        this.encoder = encoder;
        this.passwordAttempts = passwordAttempts;
        this.limiter = limiter;
        this.limits = limits;
        this.mail = mail;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Status status(UUID userId) {
        var enabled = secrets.isEnabled(userId);
        return new Status(cipher != null, enabled, enabled ? backupCodes.countByUserIdAndUsedAtIsNull(userId) : 0);
    }

    /** Whether signing in needs a code. Stays true without the key, so a lost key locks out instead of opening up. */
    @Transactional(readOnly = true)
    public boolean isEnabled(UUID userId) {
        return secrets.isEnabled(userId);
    }

    /** Starts (or restarts) the setup with a new secret; nothing changes for sign-in until a code confirms it. */
    @Transactional
    public Setup startSetup(UUID userId) {
        requireAvailable();
        var user = users.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        var existing = secrets.findLocked(userId);
        if (existing.filter(UserTwoFactor::isEnabled).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Two-step sign-in is already on");
        }
        var secret = Totp.newSecret();
        var encrypted = cipher.encrypt(secret, context(userId));
        existing.ifPresentOrElse(row -> row.setSecret(encrypted),
                () -> secrets.save(new UserTwoFactor(userId, encrypted, clock.instant())));
        return new Setup(secret, Totp.uri(ISSUER, user.getEmail(), secret));
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public BackupCodes enable(UUID userId, String code, String password) {
        requireAvailable();
        requirePassword(userId, password);
        var row = secrets.findLocked(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Start the setup first"));
        if (row.isEnabled()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Two-step sign-in is already on");
        var step = Totp.verify(cipher.decrypt(row.getSecret(), context(userId)), normalize(code), clock.instant(), null);
        if (step.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The code is not valid");
        row.enable(clock.instant());
        row.setLastUsedStep(step.getAsLong());
        var codes = replaceBackupCodes(userId);
        users.findById(userId).ifPresent(user -> mail.sendTwoFactorChanged(user, true));
        return codes;
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void disable(UUID userId, String password) {
        requirePassword(userId, password);
        if (!secrets.isEnabled(userId)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Two-step sign-in is off");
        remove(userId);
        users.findById(userId).ifPresent(user -> mail.sendTwoFactorChanged(user, false));
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public BackupCodes renewBackupCodes(UUID userId, String password) {
        requirePassword(userId, password);
        if (!secrets.isEnabled(userId)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Two-step sign-in is off");
        return replaceBackupCodes(userId);
    }

    /** Removes the secret and the codes, for example when the account is deleted. */
    @Transactional
    public void remove(UUID userId) {
        backupCodes.deleteByUserId(userId);
        secrets.findById(userId).ifPresent(secrets::delete);
        secrets.flush();
    }

    /**
     * Checks the second step of a sign-in: a current authenticator code (each works once) or an unused backup code.
     * Wrong answers are limited per person.
     */
    @Transactional(noRollbackFor = TooManyRequestsException.class)
    public boolean verifySignIn(UUID userId, String code) {
        limiter.blocked(limitKey(userId)).ifPresent(retryAfter -> { throw new TooManyRequestsException(retryAfter); });
        var row = secrets.findLocked(userId).filter(UserTwoFactor::isEnabled);
        var answer = normalize(code);
        if (row.isPresent() && answer != null && (matchesAppCode(row.get(), answer) || usesBackupCode(userId, answer))) {
            limiter.reset(limitKey(userId));
            return true;
        }
        limiter.recordFailure(limitKey(userId), limits.twoFactor());
        return false;
    }

    private boolean matchesAppCode(UserTwoFactor row, String answer) {
        if (!answer.matches("\\d{6}")) return false;
        if (cipher == null) {
            log.error("Two-step sign-in is on for an account but TWO_FACTOR_ENCRYPTION_KEY is not set: userId={}", row.getUserId());
            return false;
        }
        var step = Totp.verify(cipher.decrypt(row.getSecret(), context(row.getUserId())), answer, clock.instant(), row.getLastUsedStep());
        step.ifPresent(row::setLastUsedStep);
        return step.isPresent();
    }

    private boolean usesBackupCode(UUID userId, String answer) {
        var hash = hash(answer).getBytes(StandardCharsets.US_ASCII);
        for (var candidate : backupCodes.findByUserIdAndUsedAtIsNull(userId)) {
            if (MessageDigest.isEqual(hash, candidate.getCodeHash().getBytes(StandardCharsets.US_ASCII))) {
                candidate.use(clock.instant());
                return true;
            }
        }
        return false;
    }

    private BackupCodes replaceBackupCodes(UUID userId) {
        backupCodes.deleteByUserId(userId);
        var codes = new ArrayList<String>();
        for (int i = 0; i < BACKUP_CODES; i++) {
            var raw = new StringBuilder();
            for (int j = 0; j < 10; j++) raw.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
            backupCodes.save(new BackupCode(userId, hash(raw.toString())));
            codes.add(raw.substring(0, 5) + "-" + raw.substring(5));
        }
        return new BackupCodes(codes);
    }

    private void requirePassword(UUID userId, String password) {
        passwordAttempts.requireNotBlocked(userId);
        if (password == null || password.isEmpty() || password.length() > 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is required");
        }
        var credential = credentials.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!encoder.matches(password, credential.getPasswordHash())) {
            passwordAttempts.recordFailure(userId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        passwordAttempts.reset(userId);
    }

    private void requireAvailable() {
        if (cipher == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Two-step sign-in is not configured");
    }

    /** Digits or a backup code without spaces and dashes, in lower case; null for anything too long to be either. */
    private static String normalize(String code) {
        if (code == null || code.length() > 32) return null;
        var answer = code.replace(" ", "").replace("-", "").toLowerCase(Locale.ROOT);
        return answer.isEmpty() ? null : answer;
    }

    private static String hash(String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String context(UUID userId) {
        return "two-factor:" + userId;
    }

    private static String limitKey(UUID userId) {
        return "two-factor:" + userId;
    }

    private static TokenCipher cipher(String key, String version) {
        if (key == null || key.isBlank()) return null;
        return new TokenCipher(version == null || version.isBlank() ? "v1" : version.strip(), key, Map.of());
    }
}
