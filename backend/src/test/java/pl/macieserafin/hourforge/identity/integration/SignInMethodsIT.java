package pl.macieserafin.hourforge.identity.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import pl.macieserafin.hourforge.auth.UserCredentialRepository;
import pl.macieserafin.hourforge.identity.IdentityProvider;
import pl.macieserafin.hourforge.identity.UserIdentity;
import pl.macieserafin.hourforge.identity.UserIdentityRepository;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;
import pl.macieserafin.hourforge.support.PasswordlessAccounts;
import pl.macieserafin.hourforge.user.User;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Accounts without a password and linked Google accounts: what the profile shows and what the schema allows. */
class SignInMethodsIT extends ApiIntegrationTestSupport {
    private static final Instant LINKED_AT = Instant.parse("2026-11-01T08:30:00Z");

    @Autowired MockMvc mvc;
    @Autowired UserCredentialRepository credentials;
    @Autowired UserIdentityRepository identities;
    @Autowired UserDetailsService userDetails;
    @Autowired JdbcTemplate jdbc;

    private User user;

    @BeforeEach
    void createAccount() {
        user = PasswordlessAccounts.create(fixtures, credentials, "google-only@example.com", "Google only");
    }

    @Test
    void theProfileOfAnAccountWithoutAPasswordShowsItsLinkedGoogleAccount() throws Exception {
        identities.saveAndFlush(new UserIdentity(user, IdentityProvider.GOOGLE, "1234567890", "person@gmail.com", LINKED_AT));

        mvc.perform(get("/api/me").with(PasswordlessAccounts.signedIn(userDetails, user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.hasPassword").value(false))
                .andExpect(jsonPath("$.google.linked").value(true))
                .andExpect(jsonPath("$.google.email").value("person@gmail.com"))
                .andExpect(jsonPath("$.google.linkedAt").value("2026-11-01T08:30:00Z"))
                .andExpect(jsonPath("$.google.subject").doesNotExist());
    }

    @Test
    void renamingTheAccountAnswersWithTheSameSignInMethodsAsTheProfile() throws Exception {
        identities.saveAndFlush(new UserIdentity(user, IdentityProvider.GOOGLE, "1234567890", "person@gmail.com", LINKED_AT));

        mvc.perform(patch("/api/account/profile").with(PasswordlessAccounts.signedIn(userDetails, user)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Renamed"))
                .andExpect(jsonPath("$.hasPassword").value(false))
                .andExpect(jsonPath("$.google.linked").value(true))
                .andExpect(jsonPath("$.google.email").value("person@gmail.com"))
                .andExpect(jsonPath("$.google.linkedAt").value("2026-11-01T08:30:00Z"));
    }

    @Test
    void anAccountWithoutAPasswordKeepsWorkingSessionsAndSignsOutEverywhere() throws Exception {
        var signedIn = PasswordlessAccounts.signedIn(userDetails, user);
        mvc.perform(get("/api/me").with(signedIn)).andExpect(status().isOk()).andExpect(jsonPath("$.google").value((Object) null));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/account/sign-out-everywhere")
                        .with(signedIn).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNoContent());
        // The session version moved on, so the principal of the old session no longer counts.
        mvc.perform(get("/api/me").with(signedIn)).andExpect(status().isUnauthorized());
    }

    @Test
    void oneGoogleAccountBelongsToOneAccountAndTheSubjectIsComparedExactly() {
        identities.saveAndFlush(new UserIdentity(user, IdentityProvider.GOOGLE, "AbC-123", null, LINKED_AT));
        var other = PasswordlessAccounts.create(fixtures, credentials, "other@example.com", "Other");

        assertThatThrownBy(() -> identities.saveAndFlush(new UserIdentity(other, IdentityProvider.GOOGLE, "AbC-123", null, LINKED_AT)))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uq_user_identities_provider_subject");
    }

    @Test
    void subjectsThatDifferOnlyInCaseAreDifferentPeople() {
        identities.saveAndFlush(new UserIdentity(user, IdentityProvider.GOOGLE, "AbC-123", null, LINKED_AT));
        var other = PasswordlessAccounts.create(fixtures, credentials, "other@example.com", "Other");
        identities.saveAndFlush(new UserIdentity(other, IdentityProvider.GOOGLE, "abc-123", null, LINKED_AT));

        assertThat(identities.findByProviderAndSubject(IdentityProvider.GOOGLE, "AbC-123").orElseThrow().getUser().getId()).isEqualTo(user.getId());
        assertThat(identities.findByProviderAndSubject(IdentityProvider.GOOGLE, "abc-123").orElseThrow().getUser().getId()).isEqualTo(other.getId());
        assertThat(identities.findByProviderAndSubject(IdentityProvider.GOOGLE, "ABC-123")).isEmpty();
    }

    @Test
    void anAccountHasAtMostOneGoogleAccount() {
        identities.saveAndFlush(new UserIdentity(user, IdentityProvider.GOOGLE, "first", null, LINKED_AT));

        assertThatThrownBy(() -> identities.saveAndFlush(new UserIdentity(user, IdentityProvider.GOOGLE, "second", null, LINKED_AT)))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uq_user_identities_user_provider");
    }

    @Test
    void aSignInThroughGoogleRecordsTheTimeAndTheCurrentAddress() {
        var identity = identities.saveAndFlush(new UserIdentity(user, IdentityProvider.GOOGLE, "1234567890", "old@gmail.com", LINKED_AT));
        var usedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        identity.recordUse(usedAt, "new@gmail.com");
        identities.flush();

        var stored = jdbc.queryForMap("select email, last_used_at, linked_at from user_identities where id = ?", identity.getId());
        assertThat(stored.get("email")).isEqualTo("new@gmail.com");
        assertThat(((java.sql.Timestamp) stored.get("last_used_at")).toInstant()).isEqualTo(usedAt);
        assertThat(((java.sql.Timestamp) stored.get("linked_at")).toInstant()).isEqualTo(LINKED_AT);
    }

    // A failed statement ends the test transaction, so each refusal is the last statement of its test.
    @Test
    void aSignInWithoutAnAddressKeepsTheStoredOne() {
        var identity = identities.saveAndFlush(new UserIdentity(user, IdentityProvider.GOOGLE, "1234567890", "kept@gmail.com", LINKED_AT));
        var usedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        identity.recordUse(usedAt, null);
        identities.flush();

        var stored = jdbc.queryForMap("select email, last_used_at from user_identities where id = ?", identity.getId());
        assertThat(stored.get("email")).isEqualTo("kept@gmail.com");
        assertThat(((java.sql.Timestamp) stored.get("last_used_at")).toInstant()).isEqualTo(usedAt);
    }

    @Test
    void theSchemaKnowsOnlyGoogle() {
        assertThatThrownBy(() -> jdbc.update("insert into user_identities (id, user_id, provider, subject, linked_at) values (gen_random_uuid(), ?, 'GITHUB', 's', now())", user.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theSchemaHoldsSubjectsAndAddressesOfUpTo255Characters() {
        identities.saveAndFlush(new UserIdentity(user, IdentityProvider.GOOGLE, "s".repeat(255), "e".repeat(255), LINKED_AT));
        assertThatThrownBy(() -> jdbc.update("update user_identities set subject = ? where user_id = ?", "s".repeat(256), user.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void removingTheUserDoesNotSilentlyRemoveTheLink() {
        identities.saveAndFlush(new UserIdentity(user, IdentityProvider.GOOGLE, "1234567890", null, LINKED_AT));
        jdbc.update("delete from user_credentials where user_id = ?", user.getId());
        assertThatThrownBy(() -> jdbc.update("delete from users where id = ?", user.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theEntityRefusesValuesTheColumnsCannotHold() {
        assertThatThrownBy(() -> new UserIdentity(user, IdentityProvider.GOOGLE, "", null, LINKED_AT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UserIdentity(user, IdentityProvider.GOOGLE, "s".repeat(256), null, LINKED_AT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UserIdentity(user, IdentityProvider.GOOGLE, "s", "e".repeat(256), LINKED_AT)).isInstanceOf(IllegalArgumentException.class);
    }
}
