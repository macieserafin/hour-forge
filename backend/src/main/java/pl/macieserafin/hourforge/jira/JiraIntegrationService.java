package pl.macieserafin.hourforge.jira;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.team.TeamMember;
import pl.macieserafin.hourforge.entrytemplate.EntryTemplateRepository;
import pl.macieserafin.hourforge.team.TeamMemberRepository;
import pl.macieserafin.hourforge.team.TeamRepository;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.timeentry.TimeEntryRepository;
import pl.macieserafin.hourforge.user.UserRepository;

/** Connecting a team to a Jira Cloud site and each member's own Jira account to it. */
@Service
@ConditionalOnProperty(name = "app.jira.enabled", havingValue = "true")
public class JiraIntegrationService {
    static final Duration STATE_LIFETIME = Duration.ofMinutes(10);

    /** What the callback tells the browser; only these fixed words are ever put in the redirect. */
    public enum Outcome {
        CONNECTED, CHOOSE_SITE, DENIED, INVALID_STATE, FORBIDDEN, NO_ACCESS, UNAVAILABLE;

        public String code() {
            return name().toLowerCase().replace('_', '-');
        }
    }

    public record CallbackResult(Outcome outcome, UUID teamId) {
    }

    private final SecureRandom random = new SecureRandom();
    private final JiraAtlassianClient atlassian;
    private final JiraTokenService tokens;
    private final JiraConnectionRepository connections;
    private final JiraUserAuthorizationRepository authorizations;
    private final OAuthStateRepository states;
    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final UserRepository users;
    private final JiraSearchCache cache;
    private final TimeEntryRepository entries;
    private final EntryTemplateRepository entryTemplates;
    private final Clock clock;

    public JiraIntegrationService(JiraAtlassianClient atlassian, JiraTokenService tokens, JiraConnectionRepository connections,
                                  JiraUserAuthorizationRepository authorizations, OAuthStateRepository states, TeamRepository teams,
                                  TeamMemberRepository members, UserRepository users, JiraSearchCache cache, TimeEntryRepository entries, EntryTemplateRepository entryTemplates, Clock clock) {
        this.atlassian = atlassian;
        this.tokens = tokens;
        this.connections = connections;
        this.authorizations = authorizations;
        this.states = states;
        this.teams = teams;
        this.members = members;
        this.users = users;
        this.cache = cache;
        this.entries = entries;
        this.entryTemplates = entryTemplates;
        this.clock = clock;
    }

    public record Status(boolean available, boolean siteConnected, String siteName, String siteUrl, boolean userConnected,
                         boolean canManage) {
    }

    public record SiteChoice(String cloudId, String name, String url) {
    }

    @Transactional(readOnly = true)
    public Status status(UUID userId, UUID teamId) {
        var member = activeMember(teamId, userId);
        var connection = connections.findByTeamId(teamId);
        var connected = connection.isPresent()
                && authorizations.findByUserIdAndCloudId(userId, connection.get().getCloudId()).isPresent();
        return new Status(true, connection.isPresent(), connection.map(JiraConnection::getSiteName).orElse(null),
                connection.map(JiraConnection::getSiteUrl).orElse(null), connected, member.getRole() == TeamRole.OWNER);
    }

    /** Creates a one-time state bound to the person, the session and the team, and returns the Atlassian consent URL. */
    @Transactional
    public String startAuthorization(UUID userId, UUID teamId, OAuthState.Intent intent, String sessionId) {
        var member = activeMember(teamId, userId);
        if (intent == OAuthState.Intent.SITE) {
            if (member.getRole() != TeamRole.OWNER) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            requireNotArchived(teamId);
        } else if (connections.findByTeamId(teamId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The team owner has not connected a Jira site yet");
        }
        var now = clock.instant();
        states.deleteExpired(now.minus(Duration.ofDays(1)));
        var bytes = new byte[32];
        random.nextBytes(bytes);
        var state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        states.save(new OAuthState(hash(state), hash(sessionId), users.getReferenceById(userId), teamId, intent,
                now.plus(STATE_LIFETIME)));
        return atlassian.authorizationUrl(state);
    }

    /**
     * Finishes the consent. Never throws for bad input: every failure becomes a fixed outcome, so a forged or replayed
     * callback learns nothing and the state is spent either way.
     */
    @Transactional
    public CallbackResult complete(UUID userId, String sessionId, String code, String state, String error) {
        if (state == null || state.isBlank() || state.length() > 200) return new CallbackResult(Outcome.INVALID_STATE, null);
        var stored = states.findByStateHash(hash(state)).orElse(null);
        var now = clock.instant();
        if (stored == null || stored.getUsedAt() != null || !stored.getExpiresAt().isAfter(now)
                || !stored.getUser().getId().equals(userId) || !stored.getSessionHash().equals(hash(sessionId))) {
            return new CallbackResult(Outcome.INVALID_STATE, null);
        }
        stored.markUsed(now);
        var teamId = stored.getTeamId();
        if (error != null && !error.isBlank() || code == null || code.isBlank() || code.length() > 2048) {
            return new CallbackResult(Outcome.DENIED, teamId);
        }
        var member = members.findByTeamIdAndUserId(teamId, userId).filter(candidate -> candidate.getLeftAt() == null).orElse(null);
        if (member == null || stored.getIntent() == OAuthState.Intent.SITE && member.getRole() != TeamRole.OWNER) {
            return new CallbackResult(Outcome.FORBIDDEN, teamId);
        }
        try {
            var granted = atlassian.exchangeCode(code);
            var sites = atlassian.accessibleSites(granted.accessToken());
            var user = users.getReferenceById(userId);
            if (stored.getIntent() == OAuthState.Intent.MEMBER) {
                var connection = connections.findByTeamId(teamId).orElse(null);
                if (connection == null || sites.stream().noneMatch(site -> site.id().equals(connection.getCloudId()))) {
                    return new CallbackResult(Outcome.NO_ACCESS, teamId);
                }
                tokens.store(user, connection.getCloudId(), granted);
                return new CallbackResult(Outcome.CONNECTED, teamId);
            }
            if (sites.isEmpty()) return new CallbackResult(Outcome.NO_ACCESS, teamId);
            // The owner may have shared several sites; every one is kept until the owner picks the team's site.
            sites.forEach(site -> tokens.store(user, site.id(), granted));
            if (sites.size() == 1) {
                useSite(teamId, userId, sites.get(0));
                return new CallbackResult(Outcome.CONNECTED, teamId);
            }
            return new CallbackResult(Outcome.CHOOSE_SITE, teamId);
        } catch (JiraAtlassianClient.GrantRejectedException exception) {
            return new CallbackResult(Outcome.DENIED, teamId);
        } catch (JiraAtlassianClient.UpstreamException exception) {
            return new CallbackResult(Outcome.UNAVAILABLE, teamId);
        }
    }

    /** The sites the owner's last consent covered, read live from Atlassian. */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public List<SiteChoice> sites(UUID userId, UUID teamId) {
        requireOwner(teamId, userId);
        return ownerSites(userId).stream().map(site -> new SiteChoice(site.id(), site.name(), site.url())).toList();
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void selectSite(UUID userId, UUID teamId, String cloudId) {
        requireOwner(teamId, userId);
        requireNotArchived(teamId);
        var site = ownerSites(userId).stream().filter(candidate -> candidate.id().equals(cloudId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Site not available to you"));
        useSite(teamId, userId, site);
    }

    @Transactional
    public void disconnectUser(UUID userId, UUID teamId) {
        activeMember(teamId, userId);
        connections.findByTeamId(teamId).ifPresent(connection -> authorizations.deleteByUserIdAndCloudId(userId, connection.getCloudId()));
        cache.evictUser(userId);
    }

    @Transactional
    public void disconnectSite(UUID userId, UUID teamId) {
        requireOwner(teamId, userId);
        requireNotArchived(teamId);
        connections.findByTeamId(teamId).ifPresent(connection -> {
            removeConnection(connection, teamId);
            cache.clear();
        });
    }

    private List<JiraAtlassianClient.Site> ownerSites(UUID userId) {
        var latest = authorizations.findAllByUserIdOrderByUpdatedAtDesc(userId);
        if (latest.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Jira account is not connected");
        try {
            return atlassian.accessibleSites(tokens.accessToken(userId, latest.get(0).getCloudId()));
        } catch (JiraAtlassianClient.UpstreamException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Jira is not reachable");
        }
    }

    private void useSite(UUID teamId, UUID ownerId, JiraAtlassianClient.Site site) {
        var existing = connections.findByTeamId(teamId);
        if (existing.isPresent() && existing.get().getCloudId().equals(site.id())) return;
        existing.ifPresent(connection -> removeConnection(connection, teamId));
        connections.save(new JiraConnection(teams.getReferenceById(teamId), site.id(), site.url(), site.name(),
                users.getReferenceById(ownerId)));
        // Keep only the grant for the chosen site (and sites other teams use); the rest was offered, not chosen.
        authorizations.findAllByUserIdOrderByUpdatedAtDesc(ownerId).stream()
                .filter(row -> !row.getCloudId().equals(site.id()) && !siteInUse(row.getCloudId()))
                .forEach(authorizations::delete);
    }

    private void removeConnection(JiraConnection connection, UUID teamId) {
        entries.clearJiraLinks(connection.getId());
        entryTemplates.clearJiraLinks(connection.getId());
        connections.deleteAllByTeamId(teamId);
        releaseSite(connection.getCloudId());
    }

    /** Nobody uses a site any more: the members' grants for it are no longer needed. */
    private void releaseSite(String cloudId) {
        if (!siteInUse(cloudId)) authorizations.deleteAllByCloudId(cloudId);
    }

    private boolean siteInUse(String cloudId) {
        return connections.existsByCloudId(cloudId);
    }

    private TeamMember activeMember(UUID teamId, UUID userId) {
        return members.findByTeamIdAndUserId(teamId, userId).filter(member -> member.getLeftAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /** The site of an archived team is frozen together with its time entries. */
    private void requireNotArchived(UUID teamId) {
        var team = teams.findById(teamId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (team.isArchived()) throw new ResponseStatusException(HttpStatus.CONFLICT, "The team is archived");
    }

    private void requireOwner(UUID teamId, UUID userId) {
        if (activeMember(teamId, userId).getRole() != TeamRole.OWNER) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
