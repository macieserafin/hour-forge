package pl.macieserafin.hourforge.google.unit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import pl.macieserafin.hourforge.google.GoogleSignInConfig;

import static org.assertj.core.api.Assertions.assertThat;

/** When the Google client exists, and what a partial configuration does to the start-up (A13-D15). */
class GoogleSignInConfigTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(GoogleSignInConfig.class);

    @Test
    void anEmptyClientIdLeavesTheSignInOff() {
        runner.withPropertyValues("app.google.client-id=", "app.google.client-secret=")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ClientRegistrationRepository.class));
    }

    @Test
    void aClientIdWithoutASecretStopsTheStartUpWithAClearMessage() {
        runner.withPropertyValues("app.google.client-id=id.apps.example.test", "app.google.client-secret= ")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .rootCause().hasMessageContaining("GOOGLE_CLIENT_ID is set but GOOGLE_CLIENT_SECRET is missing"));
    }

    @Test
    void aRedirectAddressOutsideTheCallbackStopsTheStartUp() {
        runner.withPropertyValues("app.google.client-id=id.apps.example.test", "app.google.client-secret=secret-value",
                        "app.google.redirect-uri=http://localhost:5173/login/oauth2/code/google")
                .run(context -> assertThat(context).hasFailed().getFailure().rootCause().hasMessageContaining("GOOGLE_REDIRECT_URI"));
    }

    @Test
    void googleIsConfiguredWithoutAnyRequestAtStartUpWithPkceAndOnlyTheIdToken() {
        runner.withPropertyValues("app.google.client-id=id.apps.example.test", "app.google.client-secret=secret-value",
                        "app.google.redirect-uri=https://hourforge.example/api/auth/oauth2/callback/google")
                .run(context -> {
                    var google = context.getBean(ClientRegistrationRepository.class).findByRegistrationId("google");
                    assertThat(google.getClientId()).isEqualTo("id.apps.example.test");
                    assertThat(google.getClientAuthenticationMethod()).isEqualTo(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
                    assertThat(google.getScopes()).containsExactlyInAnyOrder("openid", "email", "profile");
                    assertThat(google.getRedirectUri()).isEqualTo("https://hourforge.example/api/auth/oauth2/callback/google");
                    assertThat(google.getClientSettings().isRequireProofKey()).isTrue();
                    var provider = google.getProviderDetails();
                    assertThat(provider.getIssuerUri()).isEqualTo("https://accounts.google.com");
                    assertThat(provider.getAuthorizationUri()).isEqualTo("https://accounts.google.com/o/oauth2/v2/auth");
                    assertThat(provider.getTokenUri()).isEqualTo("https://oauth2.googleapis.com/token");
                    assertThat(provider.getJwkSetUri()).isEqualTo("https://www.googleapis.com/oauth2/v3/certs");
                    assertThat(provider.getUserInfoEndpoint().getUri()).isNull();
                });
    }

    @Test
    void withoutARedirectAddressTheIncomingAddressIsUsed() {
        runner.withPropertyValues("app.google.client-id=id.apps.example.test", "app.google.client-secret=secret-value")
                .run(context -> assertThat(context.getBean(ClientRegistrationRepository.class).findByRegistrationId("google").getRedirectUri())
                        .isEqualTo("{baseUrl}/api/auth/oauth2/callback/{registrationId}"));
    }
}
