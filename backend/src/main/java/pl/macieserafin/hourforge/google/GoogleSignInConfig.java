package pl.macieserafin.hourforge.google;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Google client exists only when {@code GOOGLE_CLIENT_ID} is set ({@link GoogleSignInEnabled}). Without it there is
 * no client registration, so Spring Boot adds no OAuth2 beans and {@code SecurityConfig} adds no OAuth2 filters.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GoogleProperties.class)
public class GoogleSignInConfig {
    private static final Logger log = LoggerFactory.getLogger(GoogleSignInConfig.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    @Bean
    @Conditional(GoogleSignInEnabled.class)
    ClientRegistrationRepository googleClientRegistrations(GoogleProperties properties) {
        properties.requireComplete();
        return new InMemoryClientRegistrationRepository(registration(properties));
    }

    /** Verifies the ID token: RS256 signature with the provider's keys, issuer, audience, expiry (nonce: by Spring). */
    @Bean
    @Conditional(GoogleSignInEnabled.class)
    JwtDecoderFactory<ClientRegistration> googleIdTokenDecoders() {
        var decoders = new ConcurrentHashMap<String, JwtDecoder>();
        var keys = new RestTemplate(requestFactory());
        return registration -> decoders.computeIfAbsent(registration.getRegistrationId(), id -> {
            var decoder = NimbusJwtDecoder.withJwkSetUri(registration.getProviderDetails().getJwkSetUri())
                    .jwsAlgorithm(SignatureAlgorithm.RS256).restOperations(keys).build();
            // The same validators as Spring's default factory: timestamps, typ, certificate binding, then the OIDC claims.
            decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(new OidcIdTokenValidator(registration)));
            decoder.setClaimSetConverter(OidcIdTokenDecoderFactory.createDefaultClaimTypeConverter());
            return decoder;
        });
    }

    static RestClientAuthorizationCodeTokenResponseClient tokenClient() {
        var client = new RestClientAuthorizationCodeTokenResponseClient();
        client.setRestClient(RestClient.builder()
                .requestFactory(requestFactory())
                .configureMessageConverters(converters -> {
                    converters.addCustomConverter(new FormHttpMessageConverter());
                    converters.addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter());
                })
                .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
                .build());
        return client;
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    static ClientRegistration registration(GoogleProperties properties) {
        ClientRegistration.Builder builder;
        if (properties.usesGoogle()) {
            // Google's endpoints as its discovery document lists them; nothing is fetched at start-up.
            builder = ClientRegistration.withRegistrationId(GoogleSignIn.REGISTRATION_ID)
                    .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                    .tokenUri("https://oauth2.googleapis.com/token")
                    .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                    .issuerUri(GoogleProperties.GOOGLE_ISSUER);
        } else {
            // Only test setups point the sign-in at a stand-in; the client secret and the code go to that address.
            log.warn("Sign-in with Google does not use accounts.google.com (issuer: {}); this is meant for tests only", properties.issuerUri());
            builder = ClientRegistrations.fromOidcIssuerLocation(properties.issuerUri()).registrationId(GoogleSignIn.REGISTRATION_ID);
        }
        return builder
                .clientId(properties.clientId())
                .clientSecret(properties.clientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(properties.redirectUri() == null ? "{baseUrl}" + GoogleSignIn.CALLBACK_BASE + "/{registrationId}" : properties.redirectUri())
                .scope("openid", "email", "profile")
                // Everything comes from the ID token; no user-info request is made.
                .userInfoUri(null)
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .clientName("Google")
                // Spring adds PKCE by default only to public clients; Google supports S256 for this confidential one too.
                .clientSettings(ClientRegistration.ClientSettings.builder().requireProofKey(true).build())
                .build();
    }
}
