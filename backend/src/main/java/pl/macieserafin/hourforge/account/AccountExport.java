package pl.macieserafin.hourforge.account;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Everything the application stores about one user: profile, team memberships and own time entries. */
public record AccountExport(Instant exportedAt, Profile profile, List<Membership> teams, List<Entry> timeEntries,
                            pl.macieserafin.hourforge.preferences.PreferencesResponse preferences) {
    public record Profile(UUID id, String email, String displayName, Instant createdAt, Instant emailVerifiedAt) {
    }

    public record Membership(UUID teamId, String teamName, String role, Instant joinedAt, Instant leftAt, Instant removedAt) {
    }

    public record Entry(UUID id, UUID teamId, String teamName, UUID projectId, String projectName, String type,
                        LocalDate workDate, Instant startedAt, Instant endedAt, Integer durationSeconds,
                        double pausedSeconds, String description, Instant createdAt, Instant updatedAt,
                        String jiraIssueKey) {
    }
}
