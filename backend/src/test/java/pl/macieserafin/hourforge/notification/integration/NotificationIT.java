package pl.macieserafin.hourforge.notification.integration;

import java.time.Instant;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pl.macieserafin.hourforge.preferences.UserPreferencesRepository;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.user.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NotificationIT extends ApiIntegrationTestSupport {
    private static final Pattern LINK = Pattern.compile("/unsubscribe/([A-Za-z0-9_-]{43})\\?kind=([A-Z_]+)");

    @Autowired MockMvc mvc;
    @Autowired UserPreferencesRepository preferences;

    private User owner;
    private User worker;
    private Team team;

    @BeforeEach
    void setup() {
        owner = fixtures.user("notify-owner@example.com", "Owner");
        worker = fixtures.user("notify-worker@example.com", "Worker");
        team = fixtures.team("Notify team", "UTC");
        fixtures.member(team, owner, TeamRole.OWNER, Instant.now());
        fixtures.member(team, worker, TeamRole.MEMBER, Instant.now());
    }

    private void removeWorker() throws Exception {
        mvc.perform(as(owner, delete("/api/teams/" + team.getId() + "/members/" + worker.getId()))).andExpect(status().isNoContent());
    }

    @Test
    void optionalMailsEndWithAWorkingWayToStopThem() throws Exception {
        removeWorker();
        var matcher = LINK.matcher(mail.last().body());
        assertThat(matcher.find()).isTrue();
        assertThat(matcher.group(2)).isEqualTo("TEAM_EVENTS");
        assertThat(mail.last().body()).contains("/account");

        // The link works without signing in, only for its kind, and may be used again.
        mvc.perform(post("/api/notifications/unsubscribe").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + matcher.group(1) + "\",\"kind\":\"TEAM_EVENTS\"}")).andExpect(status().isNoContent());
        mvc.perform(post("/api/notifications/unsubscribe").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + matcher.group(1) + "\",\"kind\":\"TEAM_EVENTS\"}")).andExpect(status().isNoContent());
        var stored = preferences.findById(worker.getId()).orElseThrow();
        assertThat(stored.isTeamEventEmails()).isFalse();
        assertThat(stored.isWeeklyReportEmail()).isTrue();
        assertThat(stored.isLongTimerEmail()).isTrue();

        mail.clear();
        var other = fixtures.user("notify-other@example.com", "Other");
        fixtures.member(team, other, TeamRole.MEMBER, Instant.now());
        mail.clear();
        mvc.perform(as(owner, delete("/api/teams/" + team.getId() + "/members/" + other.getId()))).andExpect(status().isNoContent());
        assertThat(mail.sent()).hasSize(1);
    }

    @Test
    void aPersonWhoSwitchedTheKindOffGetsNoMailOfThatKind() throws Exception {
        mvc.perform(as(worker, put("/api/account/preferences")).content(prefs(",\"teamEventEmails\":false"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.teamEventEmails").value(false)).andExpect(jsonPath("$.weeklyReportEmail").value(true));
        removeWorker();
        assertThat(mail.sent()).isEmpty();
    }

    @Test
    void clientsThatDoNotSendTheNewSettingsLeaveThemAlone() throws Exception {
        mvc.perform(as(worker, put("/api/account/preferences")).content(prefs(",\"teamEventEmails\":false,\"weeklyReportEmail\":false"))).andExpect(status().isOk());
        mvc.perform(as(worker, put("/api/account/preferences")).content(prefs(""))).andExpect(status().isOk())
                .andExpect(jsonPath("$.teamEventEmails").value(false)).andExpect(jsonPath("$.weeklyReportEmail").value(false));
        mvc.perform(as(worker, get("/api/account/preferences"))).andExpect(jsonPath("$.teamEventEmails").value(false));
        mvc.perform(as(owner, get("/api/account/preferences"))).andExpect(jsonPath("$.teamEventEmails").value(true)).andExpect(jsonPath("$.weeklyReportEmail").value(true));
    }

    @Test
    void unknownOrMalformedLinksAreRefusedWithoutRevealingAnything() throws Exception {
        var unknown = "A".repeat(43);
        mvc.perform(post("/api/notifications/unsubscribe").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + unknown + "\",\"kind\":\"WEEKLY_REPORT\"}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/notifications/unsubscribe").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"short\",\"kind\":\"WEEKLY_REPORT\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/notifications/unsubscribe").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + unknown + "\",\"kind\":\"PASSWORD_RESET\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/notifications/unsubscribe").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + unknown + "\",\"kind\":\"WEEKLY_REPORT\"}")).andExpect(status().isForbidden());
    }

    private static String prefs(String extra) {
        return "{\"defaultTeamId\":null,\"startPage\":\"DASHBOARD\",\"timeFormat\":\"CLOCK\",\"weekStart\":\"MONDAY\",\"longTimerEmail\":true,"
                + "\"browserNotifications\":false,\"longTimerHours\":10,\"titleTimer\":true" + extra + "}";
    }

    private MockHttpServletRequestBuilder as(User actor, MockHttpServletRequestBuilder request) {
        return request.with(user(new AppUserPrincipal(actor.getId(), actor.getEmail(), "unused"))).with(csrf()).contentType(MediaType.APPLICATION_JSON);
    }
}
