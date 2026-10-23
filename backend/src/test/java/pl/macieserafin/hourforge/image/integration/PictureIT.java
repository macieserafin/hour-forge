package pl.macieserafin.hourforge.image.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pl.macieserafin.hourforge.auth.UserCredential;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.support.BrowserSession;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.user.User;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PictureIT extends ApiIntegrationTestSupport {
    private static final String PASSWORD = "Test-password-2026";

    @Autowired MockMvc mvc;
    @Autowired UserCredentialRepository credentials;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entityManager;

    private User owner;
    private User teammate;
    private Team team;
    private BrowserSession ownerBrowser;
    private BrowserSession teammateBrowser;
    private BrowserSession strangerBrowser;

    @BeforeEach
    void people() throws Exception {
        owner = person("pictures-owner@example.test");
        teammate = person("pictures-mate@example.test");
        person("pictures-stranger@example.test");
        team = fixtures.team("Pictures", "UTC");
        fixtures.member(team, owner, TeamRole.OWNER, Instant.now());
        fixtures.member(team, teammate, TeamRole.MEMBER, Instant.now());
        ownerBrowser = signedIn("pictures-owner@example.test");
        teammateBrowser = signedIn("pictures-mate@example.test");
        strangerBrowser = signedIn("pictures-stranger@example.test");
    }

    @Test
    void aProfilePictureIsStoredAsOurOwnJpegAndShownOnlyToTeammates() throws Exception {
        upload(ownerBrowser, "/api/account/avatar", png(640, 480)).andExpect(status().isNoContent());

        ownerBrowser.perform(get("/api/me")).andExpect(jsonPath("$.avatarUpdatedAt").value(notNullValue()));
        var picture = ownerBrowser.perform(get("/api/users/" + owner.getId() + "/avatar")).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("private")))
                .andReturn().getResponse().getContentAsByteArray();
        var image = ImageIO.read(new java.io.ByteArrayInputStream(picture));
        assertThat(image.getWidth()).isEqualTo(256);
        assertThat(image.getHeight()).isEqualTo(256);

        teammateBrowser.perform(get("/api/users/" + owner.getId() + "/avatar")).andExpect(status().isOk());
        strangerBrowser.perform(get("/api/users/" + owner.getId() + "/avatar")).andExpect(status().isNotFound());
        mvc.perform(get("/api/users/" + owner.getId() + "/avatar")).andExpect(status().isUnauthorized());

        ownerBrowser.send(delete("/api/account/avatar")).andExpect(status().isNoContent());
        ownerBrowser.perform(get("/api/users/" + owner.getId() + "/avatar")).andExpect(status().isNotFound());
        ownerBrowser.perform(get("/api/me")).andExpect(jsonPath("$.avatarUpdatedAt").value(nullValue()));
    }

    @Test
    void anythingButAPictureIsRefusedAndUploadsNeedCsrf() throws Exception {
        upload(ownerBrowser, "/api/account/avatar", "<svg onload=alert(1)>".getBytes()).andExpect(status().isBadRequest());
        upload(ownerBrowser, "/api/account/avatar", new byte[3 * 1024 * 1024]).andExpect(status().isBadRequest());
        ownerBrowser.perform(multipart(HttpMethod.PUT, "/api/account/avatar").file(new MockMultipartFile("image", "a.png", "image/png", png(10, 10))))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select count(*) from user_avatars where user_id = ?", Integer.class, owner.getId())).isZero();
    }

    @Test
    void ownersAndAdminsSetTheTeamLogoWhichOnlyMembersSee() throws Exception {
        upload(teammateBrowser, "/api/teams/" + team.getId() + "/logo", png(300, 300)).andExpect(status().isForbidden());
        upload(strangerBrowser, "/api/teams/" + team.getId() + "/logo", png(300, 300)).andExpect(status().isNotFound());
        upload(ownerBrowser, "/api/teams/" + team.getId() + "/logo", png(300, 300)).andExpect(status().isNoContent());

        teammateBrowser.perform(get("/api/teams")).andExpect(jsonPath("$[0].logoUpdatedAt").value(notNullValue()));
        teammateBrowser.perform(get("/api/teams/" + team.getId() + "/logo")).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
        strangerBrowser.perform(get("/api/teams/" + team.getId() + "/logo")).andExpect(status().isNotFound());

        ownerBrowser.send(delete("/api/teams/" + team.getId() + "/logo")).andExpect(status().isNoContent());
        teammateBrowser.perform(get("/api/teams/" + team.getId() + "/logo")).andExpect(status().isNotFound());
        teammateBrowser.perform(get("/api/teams")).andExpect(jsonPath("$[0].logoUpdatedAt").value(nullValue()));
    }

    @Test
    void anArchivedTeamKeepsItsLogo() throws Exception {
        jdbc.update("update teams set archived_at = now() where id = ?", team.getId());
        entityManager.clear();
        upload(ownerBrowser, "/api/teams/" + team.getId() + "/logo", png(300, 300)).andExpect(status().isConflict());
    }

    @Test
    void picturesGoWithTheTeamAndTheAccountAndAreInTheExport() throws Exception {
        upload(ownerBrowser, "/api/teams/" + team.getId() + "/logo", png(300, 300)).andExpect(status().isNoContent());
        upload(teammateBrowser, "/api/account/avatar", png(300, 300)).andExpect(status().isNoContent());
        teammateBrowser.perform(get("/api/account/export")).andExpect(jsonPath("$.profile.avatarJpegBase64").value(notNullValue()));

        ownerBrowser.send(delete("/api/teams/" + team.getId())).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from team_logos where team_id = ?", Integer.class, team.getId())).isZero();

        teammateBrowser.send(post("/api/account/delete").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + PASSWORD + "\"}")).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from user_avatars where user_id = ?", Integer.class, teammate.getId())).isZero();
    }

    private User person(String email) {
        var user = fixtures.user(email, email.substring(0, email.indexOf('@')));
        credentials.saveAndFlush(new UserCredential(user, encoder.encode(PASSWORD)));
        return user;
    }

    private BrowserSession signedIn(String email) throws Exception {
        var browser = new BrowserSession(mvc);
        browser.login(email, PASSWORD).andExpect(status().isNoContent());
        return browser;
    }

    private static ResultActions upload(BrowserSession browser, String path, byte[] content) throws Exception {
        return browser.send(multipart(HttpMethod.PUT, path).file(new MockMultipartFile("image", "picture.png", "image/png", content)));
    }

    private static byte[] png(int width, int height) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
}
