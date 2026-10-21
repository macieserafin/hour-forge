package pl.macieserafin.hourforge.team.integration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pl.macieserafin.hourforge.project.Project;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.tag.Tag;
import pl.macieserafin.hourforge.tag.TagRepository;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamRepository;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.timeentry.TimeEntryRepository;
import pl.macieserafin.hourforge.user.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TeamRulesIT extends ApiIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired TeamRepository teams;
    @Autowired TagRepository tags;
    @Autowired TimeEntryRepository entries;

    private final LocalDate day = LocalDate.now(ZoneOffset.UTC).minusDays(1);
    private User owner;
    private User admin;
    private User member;
    private Team team;
    private Project project;
    private String base;

    @BeforeEach
    void setup() {
        owner = fixtures.user("rules-owner@example.com", "Owner");
        admin = fixtures.user("rules-admin@example.com", "Admin");
        member = fixtures.user("rules-member@example.com", "Member");
        team = fixtures.team("Rules team", "UTC");
        fixtures.member(team, owner, TeamRole.OWNER, Instant.now());
        fixtures.member(team, admin, TeamRole.ADMIN, Instant.now());
        fixtures.member(team, member, TeamRole.MEMBER, Instant.now());
        project = fixtures.project(team, "Main", null);
        base = "/api/teams/" + team.getId();
    }

    private String settings(String extra) {
        return "{\"name\":\"Rules team\",\"timezone\":\"UTC\"" + extra + "}";
    }

    private String manual(String extra) {
        return "{\"teamId\":\"%s\",\"projectId\":\"%s\",\"workDate\":\"%s\",\"durationSeconds\":600%s}".formatted(team.getId(), project.getId(), day, extra);
    }

    @Test
    void ownersAndAdminsSetTheRulesAndEveryoneSeesThem() throws Exception {
        mvc.perform(as(admin, patch(base)).content(settings(",\"requireDescription\":true,\"weekStart\":\"SUNDAY\"")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requireDescription").value(true))
                .andExpect(jsonPath("$.weekStart").value("SUNDAY")).andExpect(jsonPath("$.requireTagOrIssue").value(false));
        mvc.perform(as(member, get("/api/teams"))).andExpect(jsonPath("$[0].requireDescription").value(true)).andExpect(jsonPath("$[0].weekStart").value("SUNDAY"));
        // A client that does not send the rules leaves them as they are.
        mvc.perform(as(owner, patch(base)).content(settings(""))).andExpect(jsonPath("$.requireDescription").value(true)).andExpect(jsonPath("$.weekStart").value("SUNDAY"));
        mvc.perform(as(member, patch(base)).content(settings(",\"requireDescription\":false"))).andExpect(status().isForbidden());
        mvc.perform(as(owner, patch(base)).content(settings(",\"weekStart\":\"FRIDAY\""))).andExpect(status().isBadRequest());
        assertThat(teams.findById(team.getId()).orElseThrow().isRequireDescription()).isTrue();
    }

    @Test
    void aRequiredDescriptionAppliesToManualEntriesEditsAndStoppingATimerButNotToCorrections() throws Exception {
        mvc.perform(as(owner, patch(base)).content(settings(",\"requireDescription\":true"))).andExpect(status().isOk());
        mvc.perform(as(member, post("/api/time-entries")).content(manual(""))).andExpect(status().isUnprocessableEntity());
        mvc.perform(as(member, post("/api/time-entries")).content(manual(",\"description\":\"   \""))).andExpect(status().isUnprocessableEntity());
        var created = mvc.perform(as(member, post("/api/time-entries")).content(manual(",\"description\":\"Did the work\"")))
                .andExpect(status().isCreated()).andReturn();
        var id = com.jayway.jsonpath.JsonPath.<String>read(created.getResponse().getContentAsString(), "$.id");
        var edit = "{\"projectId\":\"%s\",\"workDate\":\"%s\",\"durationSeconds\":700%s}";
        mvc.perform(as(member, patch("/api/time-entries/" + id)).content(edit.formatted(project.getId(), day, ""))).andExpect(status().isUnprocessableEntity());
        // An owner or admin may correct an entry without a note: the rule is for what people save about their own work.
        mvc.perform(as(admin, patch(base + "/time-entries/" + id)).content(edit.formatted(project.getId(), day, ""))).andExpect(status().isOk());

        var started = mvc.perform(as(member, post("/api/timer/start")).content("{\"teamId\":\"%s\",\"projectId\":\"%s\"}".formatted(team.getId(), project.getId())))
                .andExpect(status().isCreated()).andReturn();
        var timer = com.jayway.jsonpath.JsonPath.<String>read(started.getResponse().getContentAsString(), "$.id");
        // Make the timer long enough to stop.
        var entry = entries.findById(java.util.UUID.fromString(timer)).orElseThrow();
        entry.setStartedAt(Instant.now().minusSeconds(120));
        entries.saveAndFlush(entry);
        mvc.perform(as(member, post("/api/timer/stop")).content("{\"entryId\":\"%s\"}".formatted(timer))).andExpect(status().isUnprocessableEntity());
        mvc.perform(as(member, post("/api/timer/stop")).content("{\"entryId\":\"%s\",\"description\":\"Finished\"}".formatted(timer))).andExpect(status().isOk());
    }

    @Test
    void aRequiredTagOrTaskCannotBeSwitchedOnUntilThereIsSomethingToChooseAndIsNotAskedForWhenNothingIsLeft() throws Exception {
        mvc.perform(as(owner, patch(base)).content(settings(",\"requireTagOrIssue\":true"))).andExpect(status().isConflict());
        var review = tags.saveAndFlush(new Tag(team, "Review", null));
        mvc.perform(as(owner, patch(base)).content(settings(",\"requireTagOrIssue\":true"))).andExpect(status().isOk()).andExpect(jsonPath("$.requireTagOrIssue").value(true));
        mvc.perform(as(member, post("/api/time-entries")).content(manual(""))).andExpect(status().isUnprocessableEntity());
        mvc.perform(as(member, post("/api/time-entries")).content(manual(",\"tagId\":\"" + review.getId() + "\""))).andExpect(status().isCreated());

        review.setArchivedAt(Instant.now());
        tags.saveAndFlush(review);
        mvc.perform(as(member, post("/api/time-entries")).content(manual(""))).andExpect(status().isCreated());
        mvc.perform(as(owner, patch(base)).content(settings(",\"requireTagOrIssue\":false"))).andExpect(jsonPath("$.requireTagOrIssue").value(false));
    }

    private MockHttpServletRequestBuilder as(User actor, MockHttpServletRequestBuilder request) {
        return request.with(user(new AppUserPrincipal(actor.getId(), actor.getEmail(), "unused"))).with(csrf()).contentType(MediaType.APPLICATION_JSON);
    }
}
