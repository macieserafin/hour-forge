package pl.macieserafin.hourforge.invitation;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamMember;
import pl.macieserafin.hourforge.team.TeamMemberRepository;
import pl.macieserafin.hourforge.team.TeamRepository;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.user.User;
import pl.macieserafin.hourforge.user.UserRepository;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.frontend-url=https://hourforge.test")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InvitationTests {
    @Autowired MockMvc mvc;
    @Autowired InvitationService service;
    @Autowired TeamInviteRepository invites;
    @Autowired TeamRepository teams;
    @Autowired TeamMemberRepository members;
    @Autowired UserRepository users;

    private User owner;
    private User member;
    private User guest;
    private Team team;

    @BeforeEach
    void fixtures() {
        owner = users.saveAndFlush(new User("invite-owner@example.com", "Owner"));
        member = users.saveAndFlush(new User("invite-member@example.com", "Member"));
        guest = users.saveAndFlush(new User("invite-guest@example.com", "Guest"));
        team = teams.saveAndFlush(new Team("Invited Team", "Europe/Warsaw"));
        members.saveAndFlush(new TeamMember(team, owner, TeamRole.OWNER, Instant.now()));
        members.saveAndFlush(new TeamMember(team, member, TeamRole.MEMBER, Instant.now()));
    }

    @Test
    void ownerCreatesLinkAndDatabaseStoresOnlyHash() throws Exception {
        var response = mvc.perform(post("/api/teams/{id}/invites", team.getId()).with(user(principal(owner))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiresAt\":\"" + Instant.now().plusSeconds(3600) + "\",\"maxUses\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teamId").value(team.getId().toString()))
                .andExpect(jsonPath("$.maxUses").value(2))
                .andExpect(jsonPath("$.tokenHash").doesNotExist())
                .andReturn().getResponse();
        String token = JsonPath.read(response.getContentAsString(), "$.token");
        String url = JsonPath.read(response.getContentAsString(), "$.invitationUrl");
        var stored = invites.findById(UUID.fromString(JsonPath.read(response.getContentAsString(), "$.id"))).orElseThrow();
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(url).isEqualTo("https://hourforge.test/invite/" + token);
        assertThat(stored.getTokenHash()).matches("[a-f0-9]{64}").isNotEqualTo(token);
        assertThat(stored.getCreatedBy().getId()).isEqualTo(owner.getId());
        assertThat(stored.getUsedCount()).isZero();
    }

    @Test
    void createsDistinctTokensAndAllowsUnboundedUseCount() {
        var first = create(null);
        var second = create(null);
        assertThat(first.token()).isNotEqualTo(second.token());
        assertThat(first.maxUses()).isNull();
    }

    @Test
    void onlyActiveOwnerOfThisTeamCanGenerateInvite() throws Exception {
        String body = "{\"expiresAt\":\"" + Instant.now().plusSeconds(3600) + "\"}";
        mvc.perform(post("/api/teams/{id}/invites", team.getId()).with(user(principal(member))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/teams/{id}/invites", team.getId()).with(user(principal(guest))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/teams/{id}/invites", UUID.randomUUID()).with(user(principal(owner))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
        members.findByTeamIdAndUserId(team.getId(), owner.getId()).orElseThrow().setLeftAt(Instant.now());
        mvc.perform(post("/api/teams/{id}/invites", team.getId()).with(user(principal(owner))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"expiresAt\":null}", "{\"expiresAt\":\"2020-01-01T00:00:00Z\"}",
            "{\"expiresAt\":\"FUTURE\",\"maxUses\":0}", "{\"expiresAt\":\"FUTURE\",\"maxUses\":-1}"})
    void validatesExpirationAndOptionalLimit(String body) throws Exception {
        mvc.perform(post("/api/teams/{id}/invites", team.getId()).with(user(principal(owner))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("FUTURE", Instant.now().plusSeconds(3600).toString())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptCreatesMemberAndDoesNotConsumeLimitAgain() throws Exception {
        var invite = create(1);
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/api/invites/{token}/accept", invite.token()).with(user(principal(guest))).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(team.getId().toString()))
                    .andExpect(jsonPath("$.role").value("MEMBER"));
        }
        var membership = members.findByTeamIdAndUserId(team.getId(), guest.getId()).orElseThrow();
        assertThat(membership.getRole()).isEqualTo(TeamRole.MEMBER);
        assertThat(membership.getLeftAt()).isNull();
        assertThat(invites.findById(invite.id()).orElseThrow().getUsedCount()).isEqualTo(1);
    }

    @Test
    void existingMemberAndOwnerDoNotConsumeUsesOrLoseRole() throws Exception {
        var invite = create(1);
        for (User existing : new User[]{owner, member}) {
            mvc.perform(post("/api/invites/{token}/accept", invite.token()).with(user(principal(existing))).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value(existing == owner ? "OWNER" : "MEMBER"));
        }
        assertThat(invites.findById(invite.id()).orElseThrow().getUsedCount()).isZero();
    }

    @Test
    void reactivatesFormerMemberWithoutDuplicateAndResetsJoinDate() throws Exception {
        var former = new TeamMember(team, guest, TeamRole.MEMBER, Instant.now().minusSeconds(7200));
        former.setLeftAt(Instant.now().minusSeconds(3600));
        var previousId = members.saveAndFlush(former).getId();
        Instant before = Instant.now();
        var invite = create(1);
        mvc.perform(post("/api/invites/{token}/accept", invite.token()).with(user(principal(guest))).with(csrf()))
                .andExpect(status().isOk());
        var rejoined = members.findByTeamIdAndUserId(team.getId(), guest.getId()).orElseThrow();
        assertThat(rejoined.getId()).isEqualTo(previousId);
        assertThat(rejoined.getJoinedAt()).isBetween(before, Instant.now());
        assertThat(rejoined.getLeftAt()).isNull();
        assertThat(rejoined.getRole()).isEqualTo(TeamRole.MEMBER);
    }

    @Test
    void formerOwnerRejoinsAsMember() {
        var former = new TeamMember(team, guest, TeamRole.OWNER, Instant.now().minusSeconds(7200));
        former.setLeftAt(Instant.now().minusSeconds(3600));
        members.saveAndFlush(former);
        var invite = create(null);
        assertThat(service.accept(guest.getId(), invite.token()).role()).isEqualTo(TeamRole.MEMBER);
        assertThat(members.findByTeamIdAndUserId(team.getId(), owner.getId()).orElseThrow().getRole()).isEqualTo(TeamRole.OWNER);
    }

    @Test
    void rejectsExhaustedInviteForNewMember() throws Exception {
        var invite = create(1);
        service.accept(guest.getId(), invite.token());
        var next = users.saveAndFlush(new User("invite-next@example.com", "Next"));
        mvc.perform(post("/api/invites/{token}/accept", invite.token()).with(user(principal(next))).with(csrf()))
                .andExpect(status().isConflict());
        assertThat(members.findByTeamIdAndUserId(team.getId(), next.getId())).isEmpty();
        assertThat(invites.findById(invite.id()).orElseThrow().getUsedCount()).isEqualTo(1);
    }

    @Test
    void rejectsExpiredAndRevokedInvites() throws Exception {
        var expired = create(null);
        invites.findById(expired.id()).orElseThrow().setExpiresAt(Instant.now().minusSeconds(1));
        var revoked = create(null);
        invites.findById(revoked.id()).orElseThrow().setRevokedAt(Instant.now());
        for (String token : new String[]{expired.token(), revoked.token()}) {
            mvc.perform(post("/api/invites/{token}/accept", token).with(user(principal(guest))).with(csrf()))
                    .andExpect(status().isGone());
        }
        assertThat(members.findByTeamIdAndUserId(team.getId(), guest.getId())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void rejectsUnknownOrMalformedToken(String token) throws Exception {
        mvc.perform(post("/api/invites/{token}/accept", token).with(user(principal(guest))).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void bothOperationsRequireLoginAndCsrf() throws Exception {
        var invite = create(null);
        String body = "{\"expiresAt\":\"" + Instant.now().plusSeconds(3600) + "\"}";
        mvc.perform(post("/api/teams/{id}/invites", team.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/teams/{id}/invites", team.getId()).with(user(principal(owner)))
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/invites/{token}/accept", invite.token()).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/invites/{token}/accept", invite.token()).with(user(principal(guest))))
                .andExpect(status().isForbidden());
    }

    private InviteResponse create(Integer limit) {
        return service.create(owner.getId(), team.getId(), new CreateInviteRequest(Instant.now().plusSeconds(3600), limit));
    }

    private AppUserPrincipal principal(User account) {
        return new AppUserPrincipal(account.getId(), account.getEmail(), "unused-in-request-test");
    }
}
