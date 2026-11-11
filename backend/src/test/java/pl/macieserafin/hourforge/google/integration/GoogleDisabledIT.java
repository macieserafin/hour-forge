package pl.macieserafin.hourforge.google.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import pl.macieserafin.hourforge.google.GoogleSignIn;
import pl.macieserafin.hourforge.support.ApiIntegrationTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The default (F6): without GOOGLE_CLIENT_ID the application starts as before and nothing of Google is reachable. */
class GoogleDisabledIT extends ApiIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;
    @Autowired FilterChainProxy filters;

    @Test
    void thePublicConfigurationSaysTheSignInWithGoogleIsOff() throws Exception {
        mvc.perform(get("/api/config")).andExpect(status().isOk()).andExpect(jsonPath("$.googleSignIn").value(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/oauth2/authorization/google?mode=login", "/api/auth/oauth2/callback/google?code=c&state=s"})
    void neitherTheStartNorTheCallbackExists(String address) throws Exception {
        mvc.perform(get(address)).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/oauth2/authorization/google", "/login/oauth2/code/google?code=c&state=s"})
    void springsDefaultAddressesAreOrdinaryClosedAddresses(String address) throws Exception {
        mvc.perform(get(address)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/account/google/link", "/api/account/google/unlink"})
    void linkingAndUnlinkingDoNotExist(String address) throws Exception {
        var principal = new pl.macieserafin.hourforge.security.AppUserPrincipal(java.util.UUID.randomUUID(), "someone@example.com", "unused");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(address)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(principal))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void noClientRegistrationNorOAuth2FilterExists() {
        // Bean names only: a failure message must not print a registration, whose text includes the client secret.
        assertThat(context.getBeanNamesForType(ClientRegistrationRepository.class)).isEmpty();
        assertThat(context.getBeanNamesForType(OAuth2AuthorizedClientService.class)).isEmpty();
        assertThat(context.getBeanNamesForType(GoogleSignIn.class)).isEmpty();
        assertThat(filters.getFilterChains()).hasSize(1);
        assertThat(filters.getFilterChains().getFirst().getFilters())
                .noneMatch(filter -> filter instanceof OAuth2AuthorizationRequestRedirectFilter || filter instanceof OAuth2LoginAuthenticationFilter);
    }
}
