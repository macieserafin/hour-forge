package pl.macieserafin.hourforge;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import pl.macieserafin.hourforge.invitation.TeamInvite;
import pl.macieserafin.hourforge.invitation.TeamInviteRepository;
import pl.macieserafin.hourforge.project.Project;
import pl.macieserafin.hourforge.project.ProjectRepository;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamMember;
import pl.macieserafin.hourforge.team.TeamMemberRepository;
import pl.macieserafin.hourforge.team.TeamRepository;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.timeentry.EntryType;
import pl.macieserafin.hourforge.timeentry.TimeEntry;
import pl.macieserafin.hourforge.timeentry.TimeEntryRepository;
import pl.macieserafin.hourforge.user.User;
import pl.macieserafin.hourforge.user.UserRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DomainModelPersistenceTests {
    @Autowired UserRepository users;
    @Autowired TeamRepository teams;
    @Autowired TeamMemberRepository members;
    @Autowired TeamInviteRepository invites;
    @Autowired ProjectRepository projects;
    @Autowired TimeEntryRepository entries;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;

    private User user;
    private Team team;
    private Project project;
    private final Instant start = Instant.parse("2026-10-18T08:00:00Z");
    private final LocalDate date = LocalDate.of(2026, 10, 18);

    @BeforeEach
    void createFixtures() {
        user = users.saveAndFlush(new User("owner@example.com", "Owner"));
        team = teams.saveAndFlush(new Team("Forge", "Europe/Warsaw"));
        project = projects.saveAndFlush(new Project(team, "Backend", "Domain model"));
    }

    @Test
    void persistsAndReloadsAllSixEntities() {
        var member = members.saveAndFlush(new TeamMember(team, user, TeamRole.OWNER, start));
        var invite = invites.saveAndFlush(new TeamInvite(team, user, "token-hash", 5, start.plusSeconds(86400)));
        var entry = entries.saveAndFlush(entry(EntryType.MANUAL, null, null, 3600));
        entityManager.clear();
        assertThat(users.findById(user.getId()).orElseThrow().getCreatedAt()).isNotNull();
        assertThat(teams.findById(team.getId()).orElseThrow().getTimezone()).isEqualTo("Europe/Warsaw");
        assertThat(projects.findById(project.getId()).orElseThrow().getTeam().getId()).isEqualTo(team.getId());
        var storedMember = members.findById(member.getId()).orElseThrow();
        assertThat(storedMember.getRole()).isEqualTo(TeamRole.OWNER);
        assertThat(storedMember.getUser().getId()).isEqualTo(user.getId());
        var storedInvite = invites.findById(invite.getId()).orElseThrow();
        assertThat(storedInvite.getCreatedBy().getId()).isEqualTo(user.getId());
        assertThat(storedInvite.getUsedCount()).isZero();
        assertThat(storedInvite.getTokenHash()).isEqualTo("token-hash");
        var storedEntry = entries.findById(entry.getId()).orElseThrow();
        assertThat(storedEntry.getProject().getId()).isEqualTo(project.getId());
        assertThat(storedEntry.getWorkDate()).isEqualTo(date);
        assertThat(storedEntry.getCreatedAt()).isNotNull();
        assertThat(storedEntry.getUpdatedAt()).isNotNull();
        assertThat(storedEntry.getDurationSeconds()).isEqualTo(3600);
        assertThat(storedEntry.getStartedAt()).isNull();
    }

    @Test
    void allowsManualEntriesAlongsideOneActiveTimerAndACompletedTimer() {
        entries.save(entry(EntryType.MANUAL, null, null, 60));
        entries.save(entry(EntryType.MANUAL, null, null, 120));
        entries.save(entry(EntryType.TIMER, start, start.plusSeconds(60), 60));
        var active = entries.saveAndFlush(entry(EntryType.TIMER, start, null, null));
        entityManager.clear();
        assertThat(entries.count()).isEqualTo(4);
        assertThat(entries.findById(active.getId()).orElseThrow().getStartedAt()).isEqualTo(start);
    }

    @Test
    void rejectsSecondActiveTimerEvenInAnotherTeam() {
        entries.saveAndFlush(entry(EntryType.TIMER, start, null, null));
        var otherTeam = teams.saveAndFlush(new Team("Other", "UTC"));
        var otherProject = projects.saveAndFlush(new Project(otherTeam, "Other", null));
        assertThatThrownBy(() -> entries.saveAndFlush(new TimeEntry(user, otherTeam, otherProject,
                EntryType.TIMER, date, start, null, null, null))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsProjectFromAnotherTeam() {
        var other = teams.saveAndFlush(new Team("Other", "UTC"));
        assertThatThrownBy(() -> entries.saveAndFlush(new TimeEntry(user, other, project,
                EntryType.MANUAL, date, null, null, 60, null))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateMembership() {
        members.saveAndFlush(new TeamMember(team, user, TeamRole.MEMBER, start));
        assertThatThrownBy(() -> members.saveAndFlush(new TeamMember(team, user, TeamRole.MEMBER, start)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsSecondActiveOwner() {
        members.saveAndFlush(new TeamMember(team, user, TeamRole.OWNER, start));
        var other = users.saveAndFlush(new User("other@example.com", "Other"));
        assertThatThrownBy(() -> members.saveAndFlush(new TeamMember(team, other, TeamRole.OWNER, start)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsNewOwnerAfterPreviousOwnerLeaves() {
        var previous = new TeamMember(team, user, TeamRole.OWNER, start);
        previous.setLeftAt(start.plusSeconds(60));
        members.saveAndFlush(previous);
        var other = users.saveAndFlush(new User("other@example.com", "Other"));
        members.saveAndFlush(new TeamMember(team, other, TeamRole.OWNER, start.plusSeconds(60)));
        assertThat(members.count()).isEqualTo(2);
    }

    @Test
    void rejectsEmailIgnoringCase() {
        assertThatThrownBy(() -> users.saveAndFlush(new User("OWNER@example.com", "Duplicate")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateInvitationHash() {
        invites.saveAndFlush(new TeamInvite(team, user, "hash", null, start));
        assertThatThrownBy(() -> invites.saveAndFlush(new TeamInvite(team, user, "hash", null, start)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "'MANUAL', NULL, NULL, NULL",
            "'MANUAL', NULL, NULL, 0",
            "'MANUAL', NULL, NULL, -1",
            "'MANUAL', CURRENT_TIMESTAMP, NULL, 60",
            "'TIMER', NULL, NULL, NULL",
            "'TIMER', CURRENT_TIMESTAMP, NULL, 60",
            "'TIMER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 60",
            "'TIMER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 minute', NULL",
            "'TIMER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 minute', 0",
            "'INVALID', NULL, NULL, 60"
    })
    void rejectsInvalidEntryStates(String state) {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO time_entries (id, user_id, team_id, project_id, work_date,
                    entry_type, started_at, ended_at, duration_seconds)
                VALUES (?, ?, ?, ?, ?, %s)
                """.formatted(state), UUID.randomUUID(), user.getId(), team.getId(), project.getId(), date))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsInvalidInvitationUseLimit(int limit) {
        assertThatThrownBy(() -> invites.saveAndFlush(new TeamInvite(team, user, "hash", limit, start)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsNegativeInvitationUseCount() {
        var invite = new TeamInvite(team, user, "hash", null, start);
        invite.setUsedCount(-1);
        assertThatThrownBy(() -> invites.saveAndFlush(invite)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void preventsDeletingProjectWithHistoricalEntries() {
        entries.saveAndFlush(entry(EntryType.MANUAL, null, null, 60));
        entityManager.clear();
        assertThatThrownBy(() -> {
            projects.deleteById(project.getId());
            projects.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    private TimeEntry entry(EntryType type, Instant startedAt, Instant endedAt, Integer duration) {
        return new TimeEntry(user, team, project, type, date, startedAt, endedAt, duration, "Work");
    }
}
