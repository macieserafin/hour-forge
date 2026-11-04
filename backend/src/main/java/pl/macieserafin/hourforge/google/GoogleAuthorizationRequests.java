package pl.macieserafin.hourforge.google;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * The authorization request (state, nonce, PKCE verifier and the {@link GoogleIntent}) waits in the session, which
 * Spring Session stores in the database; the request is serializable. The callback removes it, and the intent is then
 * handed to the success and failure handlers on the request.
 */
final class GoogleAuthorizationRequests implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
    private final HttpSessionOAuth2AuthorizationRequestRepository delegate = new HttpSessionOAuth2AuthorizationRequestRepository();

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return delegate.loadAuthorizationRequest(request);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request,
                                         HttpServletResponse response) {
        delegate.saveAuthorizationRequest(authorizationRequest, request, response);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        var removed = delegate.removeAuthorizationRequest(request, response);
        if (removed != null) request.setAttribute(GoogleIntent.CURRENT, GoogleIntent.from(removed));
        return removed;
    }

    /** Spring's resolver (state, nonce and PKCE) plus the intent read from the start address. */
    static OAuth2AuthorizationRequestResolver resolver(ClientRegistrationRepository registrations) {
        var standard = new DefaultOAuth2AuthorizationRequestResolver(registrations, GoogleSignIn.AUTHORIZATION_BASE);
        return new OAuth2AuthorizationRequestResolver() {
            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
                return withIntent(standard.resolve(request), request);
            }

            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
                return withIntent(standard.resolve(request, clientRegistrationId), request);
            }
        };
    }

    private static OAuth2AuthorizationRequest withIntent(OAuth2AuthorizationRequest resolved, HttpServletRequest request) {
        if (resolved == null) return null;
        var builder = OAuth2AuthorizationRequest.from(resolved);
        GoogleIntent.fromStart(request).storeIn(builder);
        return builder.build();
    }
}
