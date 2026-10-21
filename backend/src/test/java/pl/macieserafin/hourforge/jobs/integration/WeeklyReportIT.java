package pl.macieserafin.hourforge.jobs.integration;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.macieserafin.hourforge.jobs.WeeklyReportService;
import pl.macieserafin.hourforge.preferences.UserPreferences;
import pl.macieserafin.hourforge.preferences.UserPreferencesRepository;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamRepository;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.user.User;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyReportIT extends ApiIntegrationTestSupport {
    // Monday 2026-10-19: 08:30 in Warsaw (UTC+2) is 06:30 UTC.
    private static final Instant MONDAY_MORNING = Instant.parse("2026-10-19T06:30:00Z");

    @Autowired WeeklyReportService reports;
    @Autowired TeamRepository teams;
    @Autowired UserPreferencesRepository preferences;

    private User owner;
    private User admin;
    private User member;
    private Team team;

    @BeforeEach
    void setup() {
        owner = fixtures.user("weekly-owner@example.com", "Owner");
        admin = fixtures.user("weekly-admin@example.com", "Admin");
        member = fixtures.user("weekly-member@example.com", "Member");
        team = fixtures.team("Weekly team", "Europe/Warsaw");
        fixtures.member(team, owner, TeamRole.OWNER, Instant.now());
        fixtures.member(team, admin, TeamRole.ADMIN, Instant.now());
        fixtures.member(team, member, TeamRole.MEMBER, Instant.now());
        team.setWeeklyReportEnabled(true);
        teams.saveAndFlush(team);
    }

    @Test
    void ownerAndAdminsGetThePreviousWeeksPdfOnceOnMondayMorning() {
        assertThat(reports.sendDue(MONDAY_MORNING)).isEqualTo(1);
        assertThat(mail.sent()).extracting(m -> m.to()).containsExactlyInAnyOrder(owner.getEmail(), admin.getEmail());
        assertThat(mail.last().subject()).contains("Weekly team").contains("2026-10-12 – 2026-10-18");
        assertThat(mail.last().body()).contains("/unsubscribe/").contains("WEEKLY_REPORT");
        assertThat(mail.attachments()).allMatch(a -> a.contains("hourforge-raport-2026-10-12_2026-10-18.pdf:application/pdf"));
        // Running again, even later that day, sends nothing more.
        assertThat(reports.sendDue(MONDAY_MORNING.plusSeconds(3 * 3600))).isZero();
        assertThat(mail.sent()).hasSize(2);
        // The next Monday is a new week.
        assertThat(reports.sendDue(MONDAY_MORNING.plusSeconds(7 * 86400 + 2 * 3600))).isEqualTo(1);
        assertThat(mail.sent()).hasSize(4);
    }

    @Test
    void nothingIsSentBeforeEightOnOtherDaysForDisabledOrArchivedTeams() {
        assertThat(reports.sendDue(Instant.parse("2026-10-19T05:30:00Z"))).isZero();
        assertThat(reports.sendDue(Instant.parse("2026-10-20T06:30:00Z"))).isZero();
        team.setWeeklyReportEnabled(false);
        teams.saveAndFlush(team);
        assertThat(reports.sendDue(MONDAY_MORNING)).isZero();
        team.setWeeklyReportEnabled(true);
        team.setArchivedAt(Instant.now());
        teams.saveAndFlush(team);
        assertThat(reports.sendDue(MONDAY_MORNING)).isZero();
        assertThat(mail.sent()).isEmpty();
    }

    @Test
    void peopleWhoSwitchedTheReportOffAreSkipped() {
        var stored = new UserPreferences(admin.getId());
        stored.setWeeklyReportEmail(false);
        preferences.saveAndFlush(stored);
        assertThat(reports.sendDue(MONDAY_MORNING)).isEqualTo(1);
        assertThat(mail.sent()).extracting(m -> m.to()).containsExactly(owner.getEmail());
    }
}
