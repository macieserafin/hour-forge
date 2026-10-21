package pl.macieserafin.hourforge.tag.integration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
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
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TagIT extends ApiIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired TagRepository tags;
    @Autowired TeamRepository teams;
    @Autowired TimeEntryRepository entries;

    private final LocalDate day = LocalDate.now(ZoneOffset.UTC).minusDays(1);
    private User owner;
    private User admin;
    private User member;
    private User outsider;
    private Team team;
    private Project project;
    private String base;

    @BeforeEach
    void setup() {
        owner = fixtures.user("tag-owner@example.com", "Owner");
        admin = fixtures.user("tag-admin@example.com", "Admin");
        member = fixtures.user("tag-member@example.com", "Member");
        outsider = fixtures.user("tag-outsider@example.com", "Outsider");
        team = fixtures.team("Tag team", "UTC");
        fixtures.member(team, owner, TeamRole.OWNER, Instant.now());
        fixtures.member(team, admin, TeamRole.ADMIN, Instant.now());
        fixtures.member(team, member, TeamRole.MEMBER, Instant.now());
        project = fixtures.project(team, "Main", null);
        base = "/api/teams/" + team.getId();
    }

    private Tag tag(String name) {
        return tags.saveAndFlush(new Tag(team, name, null));
    }

    @Test
    void ownersAndAdminsManageTagsAndEveryMemberCanReadThem() throws Exception {
        var created = mvc.perform(as(admin, post(base + "/tags")).content("{\"name\":\"  Review \",\"color\":\"TEAL\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Review")).andExpect(jsonPath("$.color").value("TEAL")).andReturn();
        var id = com.jayway.jsonpath.JsonPath.<String>read(created.getResponse().getContentAsString(), "$.id");
        mvc.perform(as(owner, put(base + "/tags/" + id)).content("{\"name\":\"Code review\",\"color\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Code review")).andExpect(jsonPath("$.color").doesNotExist());
        mvc.perform(as(member, get(base + "/tags"))).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(as(owner, post(base + "/tags/" + id + "/archive"))).andExpect(status().isOk()).andExpect(jsonPath("$.archivedAt").isNotEmpty());
        mvc.perform(as(member, get(base + "/tags"))).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(as(member, get(base + "/tags").param("includeArchived", "true"))).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(as(owner, put(base + "/tags/" + id)).content("{\"name\":\"x\"}")).andExpect(status().isConflict());
        mvc.perform(as(admin, post(base + "/tags/" + id + "/restore"))).andExpect(status().isOk()).andExpect(jsonPath("$.archivedAt").doesNotExist());
    }

    @Test
    void membersOutsidersAndArchivedTeamsCannotChangeTags() throws Exception {
        var existing = tag("Existing");
        mvc.perform(as(member, post(base + "/tags")).content("{\"name\":\"New\"}")).andExpect(status().isForbidden());
        mvc.perform(as(member, put(base + "/tags/" + existing.getId())).content("{\"name\":\"New\"}")).andExpect(status().isForbidden());
        mvc.perform(as(member, post(base + "/tags/" + existing.getId() + "/archive"))).andExpect(status().isForbidden());
        mvc.perform(as(outsider, post(base + "/tags")).content("{\"name\":\"New\"}")).andExpect(status().isNotFound());
        mvc.perform(as(outsider, get(base + "/tags"))).andExpect(status().isNotFound());
        mvc.perform(post(base + "/tags").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"New\"}")).andExpect(status().isUnauthorized());
        mvc.perform(as(owner, put(base + "/tags/" + UUID.randomUUID())).content("{\"name\":\"New\"}")).andExpect(status().isNotFound());
        team.setArchivedAt(Instant.now());
        teams.saveAndFlush(team);
        mvc.perform(as(owner, post(base + "/tags")).content("{\"name\":\"New\"}")).andExpect(status().isConflict());
        mvc.perform(as(member, get(base + "/tags"))).andExpect(status().isOk());
        assertThat(tags.count()).isEqualTo(1);
    }

    @Test
    void namesAreRequiredShortAndUniqueIgnoringCaseWithinATeam() throws Exception {
        var existing = tag("Meeting");
        mvc.perform(as(owner, post(base + "/tags")).content("{\"name\":\"meeting\"}")).andExpect(status().isConflict());
        mvc.perform(as(owner, post(base + "/tags")).content("{\"name\":\"   \"}")).andExpect(status().isBadRequest());
        mvc.perform(as(owner, post(base + "/tags")).content("{\"name\":\"" + "a".repeat(41) + "\"}")).andExpect(status().isBadRequest());
        mvc.perform(as(owner, post(base + "/tags")).content("{\"name\":\"Ok\",\"color\":\"PURPLE\"}")).andExpect(status().isBadRequest());
        mvc.perform(as(owner, put(base + "/tags/" + existing.getId())).content("{\"name\":\"MEETING\"}")).andExpect(status().isOk());
        var other = fixtures.team("Other", "UTC");
        fixtures.member(other, owner, TeamRole.OWNER, Instant.now());
        mvc.perform(as(owner, post("/api/teams/" + other.getId() + "/tags")).content("{\"name\":\"Meeting\"}")).andExpect(status().isCreated());
    }

    @Test
    void membersTagEntriesAndTheTagCanBeKeptOrRemovedAfterArchiving() throws Exception {
        var review = tag("Review");
        var manual = "{\"teamId\":\"%s\",\"projectId\":\"%s\",\"workDate\":\"%s\",\"durationSeconds\":600,\"tagId\":\"%s\"}";
        var response = mvc.perform(as(member, post("/api/time-entries")).content(manual.formatted(team.getId(), project.getId(), day, review.getId())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tagId").value(review.getId().toString())).andReturn();
        var entryId = com.jayway.jsonpath.JsonPath.<String>read(response.getResponse().getContentAsString(), "$.id");
        mvc.perform(as(member, get("/api/time-entries").param("teamId", team.getId().toString())))
                .andExpect(jsonPath("$.content[0].tagName").value("Review"));

        review.setArchivedAt(Instant.now());
        tags.saveAndFlush(review);
        var edit = "{\"projectId\":\"%s\",\"workDate\":\"%s\",\"durationSeconds\":700%s}";
        // Saving the entry with its now archived tag keeps the tag; a different archived tag is refused.
        mvc.perform(as(member, patch("/api/time-entries/" + entryId)).content(edit.formatted(project.getId(), day, ",\"tagId\":\"" + review.getId() + "\"")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tagId").value(review.getId().toString()));
        var archived = tag("Old");
        archived.setArchivedAt(Instant.now());
        tags.saveAndFlush(archived);
        mvc.perform(as(member, patch("/api/time-entries/" + entryId)).content(edit.formatted(project.getId(), day, ",\"tagId\":\"" + archived.getId() + "\"")))
                .andExpect(status().isConflict());
        mvc.perform(as(member, patch("/api/time-entries/" + entryId)).content(edit.formatted(project.getId(), day, "")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tagId").doesNotExist());
    }

    @Test
    void aTagOfAnotherTeamIsNotAvailable() throws Exception {
        var other = fixtures.team("Other", "UTC");
        var foreign = tags.saveAndFlush(new Tag(other, "Foreign", null));
        var manual = "{\"teamId\":\"%s\",\"projectId\":\"%s\",\"workDate\":\"%s\",\"durationSeconds\":600,\"tagId\":\"%s\"}";
        mvc.perform(as(member, post("/api/time-entries")).content(manual.formatted(team.getId(), project.getId(), day, foreign.getId())))
                .andExpect(status().isNotFound());
        mvc.perform(as(member, post("/api/time-entries")).content(manual.formatted(team.getId(), project.getId(), day, UUID.randomUUID())))
                .andExpect(status().isNotFound());
        assertThat(entries.count()).isZero();
    }

    @Test
    void timersCarryTheTagAndCanChangeIt() throws Exception {
        var review = tag("Review");
        var meeting = tag("Meeting");
        var started = mvc.perform(as(member, post("/api/timer/start")).content("{\"teamId\":\"%s\",\"projectId\":\"%s\",\"tagId\":\"%s\"}"
                        .formatted(team.getId(), project.getId(), review.getId())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tagId").value(review.getId().toString())).andReturn();
        var entryId = com.jayway.jsonpath.JsonPath.<String>read(started.getResponse().getContentAsString(), "$.id");
        mvc.perform(as(member, post("/api/timer/update")).content("{\"entryId\":\"%s\",\"projectId\":\"%s\",\"tagId\":\"%s\"}"
                        .formatted(entryId, project.getId(), meeting.getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tagId").value(meeting.getId().toString()));
    }

    @Test
    void managersKeepOrChangeTheTagWhenCorrectingAndRepeatingADayCopiesIt() throws Exception {
        var review = tag("Review");
        var manual = "{\"teamId\":\"%s\",\"projectId\":\"%s\",\"workDate\":\"%s\",\"durationSeconds\":600,\"tagId\":\"%s\"}";
        mvc.perform(as(member, post("/api/time-entries")).content(manual.formatted(team.getId(), project.getId(), day, review.getId())))
                .andExpect(status().isCreated());
        mvc.perform(as(member, post("/api/time-entries/copy-day")).content("{\"teamId\":\"%s\",\"fromDate\":\"%s\",\"toDate\":\"%s\"}"
                        .formatted(team.getId(), day, day.plusDays(1).isAfter(LocalDate.now(ZoneOffset.UTC)) ? LocalDate.now(ZoneOffset.UTC) : day.plusDays(1))))
                .andExpect(status().isOk());
        assertThat(entries.findAll()).hasSize(2).allMatch(entry -> entry.getTag() != null && entry.getTag().getId().equals(review.getId()));
        var created = mvc.perform(as(admin, post(base + "/members/" + member.getId() + "/time-entries"))
                        .content("{\"projectId\":\"%s\",\"workDate\":\"%s\",\"durationSeconds\":60,\"tagId\":\"%s\"}".formatted(project.getId(), day, review.getId())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tagId").value(review.getId().toString())).andReturn();
        assertThat(created.getResponse().getContentAsString()).contains("tagId");
    }

    @Test
    void reportsFilterAndGroupByTagAndShowItInEntriesAndTheCsv() throws Exception {
        var review = tag("Review");
        var meeting = tag("Meeting");
        for (var pair : new Object[][]{{review, 600}, {review, 300}, {meeting, 120}, {null, 60}}) {
            var entry = new pl.macieserafin.hourforge.timeentry.TimeEntry(member, team, project, pl.macieserafin.hourforge.timeentry.EntryType.MANUAL, day, null, null, (Integer) pair[1], null);
            entry.setTag((Tag) pair[0]);
            entries.saveAndFlush(entry);
        }
        var range = new String[]{"from", day.toString(), "to", day.toString()};
        var summary = get(base + "/reports/summary").param(range[0], range[1]).param(range[2], range[3]).param("tagIds", review.getId().toString());
        mvc.perform(as(owner, summary)).andExpect(jsonPath("$.totals.durationSeconds").value(900)).andExpect(jsonPath("$.totals.entryCount").value(2));
        mvc.perform(as(owner, get(base + "/reports/breakdown").param("groupBy", "TAG").param(range[0], range[1]).param(range[2], range[3])))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content", hasSize(3)))
                .andExpect(jsonPath("$.data.content[?(@.tagName=='Review')].durationSeconds").value(org.hamcrest.Matchers.contains(900)))
                .andExpect(jsonPath("$.data.content[?(@.tagName=='Meeting')].durationSeconds").value(org.hamcrest.Matchers.contains(120)));
        mvc.perform(as(owner, get(base + "/reports/entries").param(range[0], range[1]).param(range[2], range[3]).param("tagIds", meeting.getId().toString())))
                .andExpect(jsonPath("$.data.content", hasSize(1))).andExpect(jsonPath("$.data.content[0].tagName").value("Meeting"));
        mvc.perform(as(owner, get(base + "/reports/summary").param("tagIds", UUID.randomUUID().toString()))).andExpect(status().isNotFound());
        mvc.perform(as(owner, get(base + "/reports/export.csv").param(range[0], range[1]).param(range[2], range[3])))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Projekt;Tag;Zadanie Jira")))
                .andExpect(content().string(containsString(";Review;")));
    }

    private MockHttpServletRequestBuilder as(User actor, MockHttpServletRequestBuilder request) {
        return request.with(user(new AppUserPrincipal(actor.getId(), actor.getEmail(), "unused"))).with(csrf()).contentType(MediaType.APPLICATION_JSON);
    }
}
