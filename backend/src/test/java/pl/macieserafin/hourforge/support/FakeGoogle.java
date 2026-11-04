package pl.macieserafin.hourforge.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A stand-in for Google's OpenID Connect provider: discovery, JWKS, an authorization endpoint that consents at once
 * (redirects straight back with a code, as the end-to-end tests need) and a token endpoint that returns an RS256 ID
 * token. It checks the client secret, the redirect address and the PKCE verifier like Google, and puts the nonce of the
 * authorization request into the ID token. {@link #fault} breaks one thing on purpose.
 */
public final class FakeGoogle {
    public static final String CLIENT_ID = "test-google-client.apps.example.test";
    public static final String CLIENT_SECRET = "test-google-client-secret";

    /** The Google account the next consent signs in. */
    public record Account(String subject, String email, Object emailVerified, String hostedDomain, String name) {
        public static Account gmail(String subject, String email) {
            return new Account(subject, email, true, null, "Gmail Person");
        }
    }

    public enum Fault {
        NONE, WRONG_AUDIENCE, WRONG_ISSUER, UNKNOWN_KEY, WRONG_NONCE, NO_NONCE, EXPIRED, ACCESS_TOKEN_TYPE, TOKEN_SERVER_ERROR, CANCEL_CONSENT, PROVIDER_ERROR
    }

    /** What the authorization endpoint received for one consent. */
    public record Authorization(Map<String, String> parameters) {
        public String get(String name) {
            return parameters.get(name);
        }
    }

    private record Grant(Account account, String nonce, String codeChallenge, String redirectUri) {
    }

    private final HttpServer server;
    private final RSAKey key;
    private final RSAKey unknownKey;
    private final Map<String, Grant> grants = new ConcurrentHashMap<>();
    private final AtomicInteger counter = new AtomicInteger();
    private final List<Authorization> authorizations = new CopyOnWriteArrayList<>();
    private final List<String> issued = new CopyOnWriteArrayList<>();

    public final AtomicInteger tokenCalls = new AtomicInteger();
    public final AtomicInteger verifiedProofKeys = new AtomicInteger();
    public volatile Account account = Account.gmail("google-subject-1", "person@gmail.com");
    public volatile Fault fault = Fault.NONE;

    public FakeGoogle() {
        try {
            key = new RSAKeyGenerator(2048).keyID("fake-google-1").generate();
            unknownKey = new RSAKeyGenerator(2048).keyID("fake-google-1").generate();
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException | JOSEException exception) {
            throw new IllegalStateException(exception);
        }
        server.createContext("/.well-known/openid-configuration", this::discovery);
        server.createContext("/jwks", this::jwks);
        server.createContext("/authorize", this::authorize);
        server.createContext("/token", this::token);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    public String issuer() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void reset() {
        grants.clear();
        authorizations.clear();
        issued.clear();
        tokenCalls.set(0);
        verifiedProofKeys.set(0);
        account = Account.gmail("google-subject-1", "person@gmail.com");
        fault = Fault.NONE;
    }

    public List<Authorization> authorizations() {
        return authorizations;
    }

    /** Every code, access token and ID token handed out, to prove they do not reach the logs or the database. */
    public List<String> issuedSecrets() {
        return issued;
    }

    /**
     * Consents to the authorization address the application redirected to, like a browser at Google would: returns
     * the callback address (with {@code code} and {@code state}, or {@code error}) the browser is then sent to.
     */
    public URI consent(String authorizationUrl) {
        var uri = URI.create(authorizationUrl);
        if (!authorizationUrl.startsWith(issuer() + "/authorize?")) throw new IllegalArgumentException("Not an authorization address of this stand-in");
        return URI.create(answerAuthorization(query(uri.getRawQuery())));
    }

    private String answerAuthorization(Map<String, String> parameters) {
        authorizations.add(new Authorization(Map.copyOf(parameters)));
        var redirect = parameters.get("redirect_uri");
        var state = parameters.get("state");
        var separator = redirect.contains("?") ? "&" : "?";
        if (fault == Fault.CANCEL_CONSENT) return redirect + separator + "error=access_denied&state=" + encode(state);
        if (fault == Fault.PROVIDER_ERROR) return redirect + separator + "error=server_error&state=" + encode(state);
        var code = "fake-code-" + counter.incrementAndGet();
        issued.add(code);
        grants.put(code, new Grant(account, parameters.get("nonce"), parameters.get("code_challenge"), redirect));
        return redirect + separator + "code=" + encode(code) + "&state=" + encode(state);
    }

    private void discovery(HttpExchange exchange) throws IOException {
        var issuer = issuer();
        respond(exchange, 200, "{\"issuer\":\"" + issuer + "\",\"authorization_endpoint\":\"" + issuer + "/authorize\","
                + "\"token_endpoint\":\"" + issuer + "/token\",\"jwks_uri\":\"" + issuer + "/jwks\","
                + "\"response_types_supported\":[\"code\"],\"subject_types_supported\":[\"public\"],"
                + "\"id_token_signing_alg_values_supported\":[\"RS256\"],\"scopes_supported\":[\"openid\",\"email\",\"profile\"],"
                + "\"token_endpoint_auth_methods_supported\":[\"client_secret_basic\",\"client_secret_post\"],"
                + "\"code_challenge_methods_supported\":[\"plain\",\"S256\"],"
                + "\"claims_supported\":[\"aud\",\"email\",\"email_verified\",\"exp\",\"hd\",\"iat\",\"iss\",\"name\",\"sub\"]}");
    }

    private void jwks(HttpExchange exchange) throws IOException {
        respond(exchange, 200, new JWKSet(key.toPublicJWK()).toString());
    }

    private void authorize(HttpExchange exchange) throws IOException {
        var location = answerAuthorization(query(exchange.getRequestURI().getRawQuery()));
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private void token(HttpExchange exchange) throws IOException {
        tokenCalls.incrementAndGet();
        var form = query(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        if (fault == Fault.TOKEN_SERVER_ERROR) {
            respond(exchange, 500, "{\"error\":\"server_error\"}");
            return;
        }
        var expected = "Basic " + Base64.getEncoder().encodeToString((encode(CLIENT_ID) + ":" + encode(CLIENT_SECRET)).getBytes(StandardCharsets.UTF_8));
        if (!expected.equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
            respond(exchange, 401, "{\"error\":\"invalid_client\"}");
            return;
        }
        var grant = grants.remove(form.getOrDefault("code", ""));
        if (!"authorization_code".equals(form.get("grant_type")) || grant == null || !grant.redirectUri().equals(form.get("redirect_uri"))) {
            respond(exchange, 400, "{\"error\":\"invalid_grant\"}");
            return;
        }
        if (grant.codeChallenge() != null) {
            var verifier = form.get("code_verifier");
            if (verifier == null || !grant.codeChallenge().equals(s256(verifier))) {
                respond(exchange, 400, "{\"error\":\"invalid_grant\",\"error_description\":\"code_verifier\"}");
                return;
            }
            verifiedProofKeys.incrementAndGet();
        }
        var accessToken = "fake-access-" + counter.incrementAndGet();
        var idToken = idToken(grant);
        issued.add(accessToken);
        issued.add(idToken);
        respond(exchange, 200, "{\"access_token\":\"" + accessToken + "\",\"token_type\":\"Bearer\",\"expires_in\":3599,"
                + "\"scope\":\"openid https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/userinfo.profile\","
                + "\"id_token\":\"" + idToken + "\"}");
    }

    private String idToken(Grant grant) {
        var now = Instant.now();
        var account = grant.account();
        var claims = new JWTClaimsSet.Builder()
                .issuer(fault == Fault.WRONG_ISSUER ? "https://accounts.example.test" : issuer())
                .audience(fault == Fault.WRONG_AUDIENCE ? "someone-else.apps.example.test" : CLIENT_ID)
                .claim("azp", fault == Fault.WRONG_AUDIENCE ? "someone-else.apps.example.test" : CLIENT_ID)
                .subject(account.subject())
                .issueTime(Date.from(fault == Fault.EXPIRED ? now.minus(Duration.ofHours(2)) : now))
                .expirationTime(Date.from(fault == Fault.EXPIRED ? now.minus(Duration.ofHours(1)) : now.plus(Duration.ofHours(1))));
        if (account.email() != null) claims.claim("email", account.email());
        if (account.emailVerified() != null) claims.claim("email_verified", account.emailVerified());
        if (account.hostedDomain() != null) claims.claim("hd", account.hostedDomain());
        if (account.name() != null) claims.claim("name", account.name());
        if (fault == Fault.WRONG_NONCE) claims.claim("nonce", "not-the-nonce-of-this-request");
        else if (fault != Fault.NO_NONCE && grant.nonce() != null) claims.claim("nonce", grant.nonce());
        try {
            var signing = fault == Fault.UNKNOWN_KEY ? unknownKey : key;
            var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signing.getKeyID())
                    // An access token in JWT form (RFC 9068) must not pass as an ID token.
                    .type(fault == Fault.ACCESS_TOKEN_TYPE ? new JOSEObjectType("at+jwt") : JOSEObjectType.JWT).build(), claims.build());
            jwt.sign(new RSASSASigner(signing));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String s256(String verifier) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static Map<String, String> query(String raw) {
        var parameters = new LinkedHashMap<String, String>();
        if (raw == null || raw.isEmpty()) return parameters;
        for (var pair : raw.split("&")) {
            var at = pair.indexOf('=');
            var name = URLDecoder.decode(at < 0 ? pair : pair.substring(0, at), StandardCharsets.UTF_8);
            parameters.put(name, at < 0 ? "" : URLDecoder.decode(pair.substring(at + 1), StandardCharsets.UTF_8));
        }
        return parameters;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
