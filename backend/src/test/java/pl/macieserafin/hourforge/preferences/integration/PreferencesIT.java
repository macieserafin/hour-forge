package pl.macieserafin.hourforge.preferences.integration;

import jakarta.persistence.EntityManager;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamMemberRepository;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.user.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PreferencesIT extends ApiIntegrationTestSupport {
    private static final String URL = "/api/account/preferences";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired TeamMemberRepository members;

    private User person;
    private User other;
    private Team team;
    private AppUserPrincipal principal;

    @BeforeEach
    void setup() {
        person = fixtures.user("prefs@example.com", "Prefs");
        other = fixtures.user("other-prefs@example.com", "Other");
        team = fixtures.team("Studio", "Europe/Warsaw");
        fixtures.member(team, person, TeamRole.MEMBER, Instant.now());
        principal = new AppUserPrincipal(person.getId(), person.getEmail(), "unused");
    }

    private String body(String defaultTeam, String page, String format, String week, boolean email, boolean notify, int hours, boolean title) {
        return "{\"defaultTeamId\":" + (defaultTeam == null ? "null" : "\"" + defaultTeam + "\"") + ",\"startPage\":\"" + page + "\",\"timeFormat\":\""
                + format + "\",\"weekStart\":\"" + week + "\",\"longTimerEmail\":" + email + ",\"browserNotifications\":" + notify
                + ",\"longTimerHours\":" + hours + ",\"titleTimer\":" + title + "}";
    }

    private ResultActions save(AppUserPrincipal who, String json) throws Exception {
        entityManager.flush();
        return mvc.perform(put(URL).with(user(who)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void aPersonWithoutSettingsHasTheDefaults() throws Exception {
        mvc.perform(get(URL).with(user(principal))).andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultTeamId").doesNotExist()).andExpect(jsonPath("$.startPage").value("DASHBOARD"))
                .andExpect(jsonPath("$.timeFormat").value("CLOCK")).andExpect(jsonPath("$.weekStart").value("MONDAY"))
                .andExpect(jsonPath("$.longTimerEmail").value(true)).andExpect(jsonPath("$.browserNotifications").value(false))
                .andExpect(jsonPath("$.longTimerHours").value(10)).andExpect(jsonPath("$.titleTimer").value(true));
    }

    @Test
    void savesAndReturnsTheChosenSettingsOnlyForThatPerson() throws Exception {
        save(principal, body(team.getId().toString(), "TIME", "DECIMAL", "SUNDAY", false, true, 6, false)).andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultTeamId").value(team.getId().toString())).andExpect(jsonPath("$.startPage").value("TIME"))
                .andExpect(jsonPath("$.timeFormat").value("DECIMAL")).andExpect(jsonPath("$.weekStart").value("SUNDAY"))
                .andExpect(jsonPath("$.longTimerEmail").value(false)).andExpect(jsonPath("$.browserNotifications").value(true))
                .andExpect(jsonPath("$.longTimerHours").value(6)).andExpect(jsonPath("$.titleTimer").value(false));
        mvc.perform(get(URL).with(user(principal))).andExpect(jsonPath("$.startPage").value("TIME"));
        mvc.perform(get(URL).with(user(new AppUserPrincipal(other.getId(), other.getEmail(), "unused"))))
                .andExpect(jsonPath("$.startPage").value("DASHBOARD"));
        save(principal, body(null, "DASHBOARD", "CLOCK", "MONDAY", true, false, 10, true)).andExpect(jsonPath("$.defaultTeamId").doesNotExist());
        assertThat(jdbc.queryForObject("select count(*) from user_preferences where user_id = ?", Integer.class, person.getId())).isEqualTo(1);
    }

    @Test
    void theDefaultTeamMustBeOneOfThePersonsTeamsAndStopsBeingOfferedAfterLeaving() throws Exception {
        var foreign = fixtures.team("Foreign", "UTC");
        save(principal, body(foreign.getId().toString(), "DASHBOARD", "CLOCK", "MONDAY", true, false, 10, true)).andExpect(status().isBadRequest());
        save(principal, body(java.util.UUID.randomUUID().toString(), "DASHBOARD", "CLOCK", "MONDAY", true, false, 10, true)).andExpect(status().isBadRequest());

        save(principal, body(team.getId().toString(), "DASHBOARD", "CLOCK", "MONDAY", true, false, 10, true)).andExpect(status().isOk());
        var membership = members.findByTeamIdAndUserId(team.getId(), person.getId()).orElseThrow();
        membership.setLeftAt(Instant.now());
        members.saveAndFlush(membership);
        mvc.perform(get(URL).with(user(principal))).andExpect(jsonPath("$.defaultTeamId").doesNotExist());
    }

    @Test
    void refusesValuesOutsideTheAllowedChoices() throws Exception {
        save(principal, body(null, "DASHBOARD", "CLOCK", "MONDAY", true, false, 3, true)).andExpect(status().isBadRequest());
        save(principal, body(null, "DASHBOARD", "CLOCK", "MONDAY", true, false, 25, true)).andExpect(status().isBadRequest());
        save(principal, body(null, "SECRET_PAGE", "CLOCK", "MONDAY", true, false, 10, true)).andExpect(status().isBadRequest());
        save(principal, body(null, "DASHBOARD", "ROMAN", "MONDAY", true, false, 10, true)).andExpect(status().isBadRequest());
        save(principal, body(null, "DASHBOARD", "CLOCK", "FRIDAY", true, false, 10, true)).andExpect(status().isBadRequest());
        save(principal, "{}").andExpect(status().isBadRequest());
    }

    @Test
    void needsSignInAndACsrfToken() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(put(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(null, "TIME", "CLOCK", "MONDAY", true, false, 10, true)))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(URL).with(user(principal)).contentType(MediaType.APPLICATION_JSON).content(body(null, "TIME", "CLOCK", "MONDAY", true, false, 10, true)))
                .andExpect(status().isForbidden());
    }

    @Test
    void theAccountExportContainsThemAndDeletingTheAccountRemovesThem() throws Exception {
        save(principal, body(null, "PROJECTS", "DECIMAL", "MONDAY", true, false, 12, true)).andExpect(status().isOk());
        mvc.perform(get("/api/account/export").with(user(principal))).andExpect(status().isOk())
                .andExpect(jsonPath("$.preferences.startPage").value("PROJECTS")).andExpect(jsonPath("$.preferences.longTimerHours").value(12));
    }

    @Test
    void theDashboardWeekStartsOnTheChosenDay() throws Exception {
        for (var first : DayOfWeek.values()) {
            if (first != DayOfWeek.MONDAY && first != DayOfWeek.SUNDAY) continue;
            var json = mvc.perform(get("/api/dashboard/summary").param("teamId", team.getId().toString())
                            .param("weekStart", first.name()).with(user(principal))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.days.length()").value(7)).andReturn().getResponse().getContentAsString();
            var weekStart = LocalDate.parse(com.jayway.jsonpath.JsonPath.<String>read(json, "$.weekStart"));
            assertThat(weekStart.getDayOfWeek()).isEqualTo(first);
            assertThat(com.jayway.jsonpath.JsonPath.<String>read(json, "$.days[0].date")).isEqualTo(weekStart.toString());
        }
        mvc.perform(get("/api/dashboard/summary").param("teamId", team.getId().toString()).param("weekStart", "FRIDAY").with(user(principal)))
                .andExpect(status().isBadRequest());
    }
}
