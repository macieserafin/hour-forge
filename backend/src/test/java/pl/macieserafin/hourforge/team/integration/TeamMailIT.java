package pl.macieserafin.hourforge.team.integration;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pl.macieserafin.hourforge.security.AppUserPrincipal;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.support.RecordingEmailSender;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.user.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// A low limit keeps the sending-limit scenario short.
@SpringBootTest(properties = "app.rate-limit.invitation-email.max=2")
class TeamMailIT extends ApiIntegrationTestSupport {
    @Autowired MockMvc mvc;

    private User owner;
    private User admin;
    private User worker;
    private User peer;
    private Team team;
    private String base;

    @BeforeEach
    void setup() {
        mail.clear();
        owner = fixtures.user("mail-owner@example.com", "Owner");
        admin = fixtures.user("mail-admin@example.com", "Admin");
        worker = fixtures.user("mail-worker@example.com", "Worker");
        peer = fixtures.user("mail-peer@example.com", "Peer");
        team = fixtures.team("Mail team", "UTC");
        fixtures.member(team, owner, TeamRole.OWNER, Instant.now());
        fixtures.member(team, admin, TeamRole.ADMIN, Instant.now());
        fixtures.member(team, worker, TeamRole.MEMBER, Instant.now());
        fixtures.member(team, peer, TeamRole.MEMBER, Instant.now());
        base = "/api/teams/" + team.getId();
    }

    private long sentTo(String address) {
        return mail.sent().stream().filter(message -> message.to().equals(address)).count();
    }

    @Test
    void anInvitationCanBeMailedAndTheLinkWorksLikeAnyOther() throws Exception {
        mvc.perform(as(admin, post(base + "/invites")).content("{\"email\":\"  guest@example.com \",\"maxUses\":1}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.invitationUrl").isNotEmpty());
        assertThat(mail.sent()).hasSize(1);
        var message = mail.last();
        assertThat(message.to()).isEqualTo("guest@example.com");
        assertThat(message.subject()).contains("Mail team");
        assertThat(message.body()).contains("Admin zaprasza Cię").contains("/invite/");
        assertThat(mail.lastToken("invite")).hasSize(43);
    }

    @Test
    void withoutAnAddressNothingIsMailedAndBadAddressesOrMembersAreRefused() throws Exception {
        mvc.perform(as(owner, post(base + "/invites")).content("{}")).andExpect(status().isCreated());
        assertThat(mail.sent()).isEmpty();
        mvc.perform(as(owner, post(base + "/invites")).content("{\"email\":\"not-an-address\"}")).andExpect(status().isBadRequest());
        mvc.perform(as(worker, post(base + "/invites")).content("{\"email\":\"guest@example.com\"}")).andExpect(status().isForbidden());
        assertThat(mail.sent()).isEmpty();
    }

    @Test
    void mailedInvitationsAreLimitedPerPersonButLinksAreNot() throws Exception {
        for (var address : new String[]{"one@example.com", "two@example.com"}) {
            mvc.perform(as(owner, post(base + "/invites")).content("{\"email\":\"" + address + "\"}")).andExpect(status().isCreated());
        }
        mvc.perform(as(owner, post(base + "/invites")).content("{\"email\":\"three@example.com\"}")).andExpect(status().isTooManyRequests());
        assertThat(mail.sent()).hasSize(2);
        mvc.perform(as(owner, post(base + "/invites")).content("{}")).andExpect(status().isCreated());
    }

    @Test
    void peopleAreToldWhenTheyAreRemovedMadeAdminOrWhenOwnershipOrArchivingHappens() throws Exception {
        mvc.perform(as(owner, put(base + "/members/" + worker.getId() + "/role")).content("{\"role\":\"ADMIN\"}")).andExpect(status().isNoContent());
        assertThat(sentTo(worker.getEmail())).isEqualTo(1);
        assertThat(mail.last().subject()).contains("administratorem");
        // The same role again changes nothing and says nothing.
        mvc.perform(as(owner, put(base + "/members/" + worker.getId() + "/role")).content("{\"role\":\"ADMIN\"}")).andExpect(status().isNoContent());
        assertThat(sentTo(worker.getEmail())).isEqualTo(1);
        mvc.perform(as(owner, put(base + "/members/" + worker.getId() + "/role")).content("{\"role\":\"MEMBER\"}")).andExpect(status().isNoContent());
        assertThat(mail.last().subject()).contains("Nie jesteś już administratorem");

        mail.clear();
        mvc.perform(as(owner, delete(base + "/members/" + peer.getId()))).andExpect(status().isNoContent());
        assertThat(mail.sent()).hasSize(1);
        assertThat(mail.last().to()).isEqualTo(peer.getEmail());
        mvc.perform(as(owner, delete(base + "/members/" + peer.getId()))).andExpect(status().isNoContent());
        assertThat(mail.sent()).hasSize(1);

        mail.clear();
        mvc.perform(as(owner, post(base + "/transfer-ownership")).content("{\"userId\":\"" + admin.getId() + "\"}")).andExpect(status().isNoContent());
        assertThat(sentTo(admin.getEmail())).isEqualTo(1);
        assertThat(sentTo(owner.getEmail())).isEqualTo(1);
        assertThat(mail.sent()).hasSize(2);

        mail.clear();
        mvc.perform(as(admin, post(base + "/archive"))).andExpect(status().isOk());
        assertThat(mail.sent()).extracting(RecordingEmailSender.Message::to).containsExactlyInAnyOrder(owner.getEmail(), worker.getEmail());
    }

    @Test
    void peopleWhoDeletedTheirAccountAreNotMailed() throws Exception {
        entityManager.flush();
        jdbc.update("update users set deleted_at = now() where id = ?", worker.getId());
        entityManager.clear();
        mvc.perform(as(owner, delete(base + "/members/" + worker.getId()))).andExpect(status().isNoContent());
        assertThat(mail.sent()).isEmpty();
    }

    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entityManager;

    private MockHttpServletRequestBuilder as(User actor, MockHttpServletRequestBuilder request) {
        return request.with(user(new AppUserPrincipal(actor.getId(), actor.getEmail(), "unused"))).with(csrf()).contentType(MediaType.APPLICATION_JSON);
    }
}
