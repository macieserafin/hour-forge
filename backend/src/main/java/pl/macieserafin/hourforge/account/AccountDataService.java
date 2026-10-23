package pl.macieserafin.hourforge.account;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.auth.AccountMailService;
import pl.macieserafin.hourforge.auth.EmailTokenRepository;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.entrytemplate.EntryTemplateRepository;
import pl.macieserafin.hourforge.image.ImageStore;
import pl.macieserafin.hourforge.invitation.TeamInviteRepository;
import pl.macieserafin.hourforge.jira.JiraUserAuthorizationRepository;
import pl.macieserafin.hourforge.jira.OAuthStateRepository;
import pl.macieserafin.hourforge.preferences.PreferencesService;
import pl.macieserafin.hourforge.preferences.UserPreferencesRepository;
import pl.macieserafin.hourforge.security.PasswordChangeAttempts;
import pl.macieserafin.hourforge.security.RateLimitProperties;
import pl.macieserafin.hourforge.security.RateLimiter;
import pl.macieserafin.hourforge.security.TooManyRequestsException;
import pl.macieserafin.hourforge.session.LoginEventRepository;
import pl.macieserafin.hourforge.team.TeamMemberRepository;
import pl.macieserafin.hourforge.team.TeamRepository;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.timeentry.EntryType;
import pl.macieserafin.hourforge.timeentry.TimeEntryRepository;
import pl.macieserafin.hourforge.twofactor.TwoFactorService;
import pl.macieserafin.hourforge.user.UserRepository;

import java.time.Clock;
import java.util.UUID;

/** The user's data rights: a copy of their data and deletion of the account (anonymization). */
@Service
public class AccountDataService {
    public static final String DELETED_USER_NAME = "Użytkownik usunięty";

    private final UserRepository users;
    private final UserCredentialRepository credentials;
    private final TeamMemberRepository members;
    private final TeamRepository teams;
    private final TimeEntryRepository entries;
    private final TeamInviteRepository invites;
    private final EmailTokenRepository tokens;
    private final JiraUserAuthorizationRepository jiraAuthorizations;
    private final OAuthStateRepository oauthStates;
    private final UserPreferencesRepository preferenceRows;
    private final EntryTemplateRepository entryTemplates;
    private final LoginEventRepository logins;
    private final TwoFactorService twoFactor;
    private final ImageStore images;
    private final PreferencesService preferences;
    private final PasswordEncoder encoder;
    private final PasswordChangeAttempts attempts;
    private final AccountMailService mail;
    private final RateLimiter limiter;
    private final RateLimitProperties limits;
    private final Clock clock;

    public AccountDataService(UserRepository users, UserCredentialRepository credentials, TeamMemberRepository members,
                              TeamRepository teams, TimeEntryRepository entries, TeamInviteRepository invites,
                              EmailTokenRepository tokens, JiraUserAuthorizationRepository jiraAuthorizations,
                              OAuthStateRepository oauthStates, UserPreferencesRepository preferenceRows, EntryTemplateRepository entryTemplates,
                              LoginEventRepository logins, TwoFactorService twoFactor, ImageStore images,
                              PreferencesService preferences, PasswordEncoder encoder, PasswordChangeAttempts attempts,
                              AccountMailService mail, RateLimiter limiter, RateLimitProperties limits, Clock clock) {
        this.users = users;
        this.credentials = credentials;
        this.members = members;
        this.teams = teams;
        this.entries = entries;
        this.invites = invites;
        this.tokens = tokens;
        this.jiraAuthorizations = jiraAuthorizations;
        this.oauthStates = oauthStates;
        this.preferenceRows = preferenceRows;
        this.entryTemplates = entryTemplates;
        this.logins = logins;
        this.twoFactor = twoFactor;
        this.images = images;
        this.preferences = preferences;
        this.encoder = encoder;
        this.attempts = attempts;
        this.mail = mail;
        this.limiter = limiter;
        this.limits = limits;
        this.clock = clock;
    }

    /** Own entries from every team, including teams the user has left, as decided for former members. */
    @Transactional(readOnly = true)
    public AccountExport export(UUID userId) {
        var user = users.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        limiter.tryAcquire("data-export:" + userId, limits.dataExport())
                .ifPresent(retryAfter -> { throw new TooManyRequestsException(retryAfter); });
        var teamsOfUser = members.findAllByUserId(userId).stream()
                .map(member -> new AccountExport.Membership(member.getTeam().getId(), member.getTeam().getName(),
                        member.getRole().name(), member.getJoinedAt(), member.getLeftAt(), member.getRemovedAt(),
                        member.getJobTitle(), member.getFtePercent())).toList();
        var timeEntries = entries.findByUserIdOrderByCreatedAtAscIdAsc(userId).stream()
                .map(entry -> new AccountExport.Entry(entry.getId(), entry.getTeam().getId(), entry.getTeam().getName(),
                        entry.getProject().getId(), entry.getProject().getName(), entry.getEntryType().name(),
                        entry.getWorkDate(), entry.getStartedAt(), entry.getEndedAt(), entry.getDurationSeconds(),
                        entry.getPausedDurationMicros() / 1_000_000.0, entry.getDescription(), entry.getCreatedAt(),
                        entry.getUpdatedAt(), entry.getJiraIssueKey(),
                        entry.getTag() == null ? null : entry.getTag().getName())).toList();
        return new AccountExport(clock.instant(),
                new AccountExport.Profile(user.getId(), user.getEmail(), user.getDisplayName(), user.getCreatedAt(),
                        user.getEmailVerifiedAt(), images.avatar(userId).map(java.util.Base64.getEncoder()::encodeToString).orElse(null)),
                teamsOfUser, timeEntries, preferences.get(userId),
                logins.findByUserIdOrderByOccurredAtAsc(userId).stream()
                        .map(login -> new AccountExport.Login(login.getOccurredAt(), login.getOutcome().name(), login.getBrowser(),
                                login.getSystem(), login.getAddress(), login.getLocation())).toList());
    }

    /**
     * Anonymizes the account: name, address and password are removed, time entries stay in the reports of the
     * teams under a placeholder name. Teams the user owns must be handed over or deleted first.
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void deleteAccount(UUID userId, String password) {
        attempts.requireNotBlocked(userId);
        if (password == null || password.isEmpty() || password.length() > 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is required");
        }
        var credential = credentials.findLockedByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!encoder.matches(password, credential.getPasswordHash())) {
            attempts.recordFailure(userId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        anonymize(userId);
        attempts.reset(userId);
    }

    /**
     * Removal by the instance operator (abuse) or by the retention of unused accounts: the same anonymization, without
     * the password. Owned teams still have to be handed over or deleted first.
     */
    @Transactional
    public void deleteWithoutPassword(UUID userId) {
        var user = users.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (user.getDeletedAt() != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "The account is already deleted");
        anonymize(userId);
    }

    private void anonymize(UUID userId) {
        var credential = credentials.findLockedByUserId(userId);
        // Same locks as ownership transfer and team deletion, taken in a fixed order, before the roles are read.
        users.findLockedForTimeEntryById(userId);
        members.findActiveTeamIds(userId).forEach(teams::findLockedById);
        var active = members.findActiveByUserId(userId);
        if (active.stream().anyMatch(member -> member.getRole() == TeamRole.OWNER)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hand over or delete the teams you own first");
        }
        var now = clock.instant();
        entries.findByUserIdAndEntryTypeAndEndedAtIsNull(userId, EntryType.TIMER).ifPresent(entries::delete);
        active.forEach(member -> { member.setLeftAt(now); member.setProfile(null, null); });
        invites.revokeActiveCreatedBy(userId, now);
        tokens.deleteByUserId(userId);
        jiraAuthorizations.deleteAllByUserId(userId);
        oauthStates.deleteAllByUserId(userId);
        preferenceRows.deleteByUserId(userId);
        entryTemplates.deleteAllByUserId(userId);
        logins.deleteByUserId(userId);
        twoFactor.remove(userId);
        images.deleteAvatar(userId);
        var user = users.findById(userId).orElseThrow();
        var oldEmail = user.getEmail();
        credential.ifPresent(credentials::delete);
        user.anonymize("deleted-" + userId + "@deleted.invalid", DELETED_USER_NAME, now);
        users.flush();
        mail.sendAccountDeleted(oldEmail);
    }
}
