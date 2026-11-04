package pl.macieserafin.hourforge.account;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Everything the application stores about one user: profile, team memberships and own time entries. */
public record AccountExport(Instant exportedAt, Profile profile, List<Membership> teams, List<Entry> timeEntries,
                            pl.macieserafin.hourforge.preferences.PreferencesResponse preferences, List<Login> logins, List<Template> templates,
                            List<JiraAuthorization> jiraAuthorizations, TwoFactor twoFactor, SignInMethods signInMethods) {
    /** How the person can sign in; {@code google} is null without a linked Google account. */
    public record SignInMethods(boolean password, GoogleIdentity google) {
    }

    /**
     * The linked Google account: its address when last seen and Google's identifier of the person ({@code sub}),
     * which is personal data rather than a secret. No Google tokens are stored, so none are exported.
     */
    public record GoogleIdentity(String email, Instant linkedAt, Instant lastUsedAt, String subject) {
    }

    /** A saved entry template of the person. */
    public record Template(UUID id, UUID teamId, String teamName, String name, UUID projectId, String description,
                           String tagName, String jiraIssueKey, Instant createdAt, Instant updatedAt) {
    }

    /** The person's own consent to a Jira site; the stored access tokens are never part of an export. */
    public record JiraAuthorization(String cloudId, String grantedScopes, Instant connectedAt, Instant updatedAt) {
    }

    /** Whether two-step sign-in is on and how many backup codes are left; the secret and the codes themselves are not exported. */
    public record TwoFactor(boolean enabled, long backupCodesLeft) {
    }

    /** A stored sign-in (kept for 90 days), as shown in the account settings; {@code method} is PASSWORD or GOOGLE. */
    public record Login(Instant occurredAt, String outcome, String method, String browser, String system, String address, String location) {
    }

    /** The profile picture, when there is one, as the stored 256x256 JPEG in base64. */
    public record Profile(UUID id, String email, String displayName, Instant createdAt, Instant emailVerifiedAt, String avatarJpegBase64) {
    }

    public record Membership(UUID teamId, String teamName, String role, Instant joinedAt, Instant leftAt, Instant removedAt,
                            String jobTitle, Integer ftePercent) {
    }

    public record Entry(UUID id, UUID teamId, String teamName, UUID projectId, String projectName, String type,
                        LocalDate workDate, Instant startedAt, Instant endedAt, Integer durationSeconds,
                        double pausedSeconds, String description, Instant createdAt, Instant updatedAt,
                        String jiraIssueKey, String tagName, Instant lastEditedAt, String lastEditedBy) {
    }
}
