package pl.macieserafin.hourforge.jira;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.user.User;

/** Stores each person's Jira tokens encrypted and hands out a valid access token, refreshing it when needed. */
@Service
@ConditionalOnProperty(name = "app.jira.enabled", havingValue = "true")
public class JiraTokenService {
    private static final Duration REFRESH_MARGIN = Duration.ofSeconds(60);

    private final JiraUserAuthorizationRepository authorizations;
    private final JiraAtlassianClient atlassian;
    private final TokenCipher cipher;
    private final Clock clock;

    public JiraTokenService(JiraUserAuthorizationRepository authorizations, JiraAtlassianClient atlassian, TokenCipher cipher,
                            Clock clock) {
        this.authorizations = authorizations;
        this.atlassian = atlassian;
        this.cipher = cipher;
        this.clock = clock;
    }

    /** Saves (or replaces) the grant of one person for one site. */
    @Transactional
    public void store(User user, String cloudId, JiraAtlassianClient.TokenResponse tokens) {
        var existing = authorizations.findByUserIdAndCloudId(user.getId(), cloudId);
        if (existing.isPresent()) {
            write(existing.get(), tokens);
        } else {
            var created = new JiraUserAuthorization(user, cloudId, "pending", null, expiry(tokens), tokens.scope(), cipher.activeVersion());
            write(created, tokens);
            authorizations.save(created);
        }
    }

    /**
     * A usable access token of this person for this site. Concurrent callers queue on the row lock, so a rotating
     * refresh token is spent once; the later callers then read the fresh token.
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public String accessToken(UUID userId, String cloudId) {
        var rows = authorizations.findAllLockedByUserId(userId);
        var row = rows.stream().filter(candidate -> candidate.getCloudId().equals(cloudId)).findFirst()
                .orElseThrow(JiraTokenService::notConnected);
        var now = clock.instant();
        if (row.getAccessTokenExpiresAt().isAfter(now.plus(REFRESH_MARGIN))) {
            var access = decrypt(row.getEncryptedAccessToken(), row);
            rewrapIfOutdated(row, access);
            return access;
        }
        if (row.getEncryptedRefreshToken() == null) {
            authorizations.deleteAll(rows.stream().filter(candidate -> sameGrant(candidate, row, null)).toList());
            throw notConnected();
        }
        var refreshToken = decrypt(row.getEncryptedRefreshToken(), row);
        try {
            var fresh = atlassian.refresh(refreshToken);
            // The old refresh token is dead now, so every row that carries the same grant gets the new tokens.
            for (var candidate : rows) {
                if (sameGrant(candidate, row, refreshToken)) write(candidate, fresh);
            }
            return fresh.accessToken();
        } catch (JiraAtlassianClient.GrantRejectedException exception) {
            authorizations.deleteAll(rows.stream().filter(candidate -> sameGrant(candidate, row, refreshToken)).toList());
            throw notConnected();
        } catch (JiraAtlassianClient.UpstreamException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Jira is not reachable");
        }
    }

    /** After a key rotation, values written with an older key are rewritten with the active one when they are next used. */
    private void rewrapIfOutdated(JiraUserAuthorization row, String access) {
        var refreshStored = row.getEncryptedRefreshToken();
        if (!cipher.needsRewrap(row.getEncryptedAccessToken()) && (refreshStored == null || !cipher.needsRewrap(refreshStored))) return;
        var context = context(row);
        var refresh = refreshStored == null ? null : cipher.encrypt(decrypt(refreshStored, row), context);
        row.update(cipher.encrypt(access, context), refresh, row.getAccessTokenExpiresAt(), row.getGrantedScopes(), cipher.activeVersion());
    }

    /** Drops one person's grant for a site, for example after Atlassian stopped accepting it. */
    @Transactional
    public void forget(UUID userId, String cloudId) {
        authorizations.deleteByUserIdAndCloudId(userId, cloudId);
    }

    private boolean sameGrant(JiraUserAuthorization candidate, JiraUserAuthorization row, String refreshToken) {
        if (candidate == row) return true;
        if (refreshToken == null || candidate.getEncryptedRefreshToken() == null) return false;
        try {
            return refreshToken.equals(cipher.decrypt(candidate.getEncryptedRefreshToken(), context(candidate)));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void write(JiraUserAuthorization row, JiraAtlassianClient.TokenResponse tokens) {
        var context = context(row);
        var refresh = tokens.refreshToken() == null ? null : cipher.encrypt(tokens.refreshToken(), context);
        row.update(cipher.encrypt(tokens.accessToken(), context), refresh, expiry(tokens), tokens.scope(), cipher.activeVersion());
    }

    private Instant expiry(JiraAtlassianClient.TokenResponse tokens) {
        return clock.instant().plusSeconds(Math.max(tokens.expiresInSeconds(), 0));
    }

    private String decrypt(String stored, JiraUserAuthorization row) {
        try {
            return cipher.decrypt(stored, context(row));
        } catch (IllegalArgumentException exception) {
            // Unreadable (key lost or data damaged): the person simply has to connect again.
            authorizations.delete(row);
            throw notConnected();
        }
    }

    /** Binds a ciphertext to its person and site, so it does not decrypt when copied to another row. */
    private static String context(JiraUserAuthorization row) {
        return row.getUser().getId() + ":" + row.getCloudId();
    }

    private static ResponseStatusException notConnected() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Jira account is not connected");
    }
}
