package pl.macieserafin.hourforge.jira.integration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A stand-in for auth.atlassian.com and api.atlassian.com. Refresh tokens rotate exactly like the real ones: using a
 * refresh token kills it and issues a new one.
 */
public final class FakeAtlassian {
    public record Site(String id, String url, String name) {
    }

    private final HttpServer server;
    private final AtomicInteger generation = new AtomicInteger();
    private final Set<String> liveRefreshTokens = ConcurrentHashMap.newKeySet();
    private final Set<String> liveAccessTokens = ConcurrentHashMap.newKeySet();
    private final List<String> requests = new CopyOnWriteArrayList<>();

    public final AtomicInteger refreshCalls = new AtomicInteger();
    public volatile long expiresInSeconds = 3600;
    public volatile long refreshDelayMillis = 0;
    public volatile int tokenEndpointFailure = 0;
    public record Issue(String id, String key, String summary) {
    }

    private final List<String> searchBodies = new CopyOnWriteArrayList<>();
    public final AtomicInteger searchCalls = new AtomicInteger();
    /** When not 0, the search answers with this status (429 also sends Retry-After). */
    public volatile int searchFailure = 0;
    public final AtomicInteger issueCalls = new AtomicInteger();
    public volatile int issueFailure = 0;
    public volatile List<Issue> issues = List.of(new Issue("10001", "PROJ-1", "First task"), new Issue("10002", "PROJ-2", "Second task"));
    public volatile List<Site> sites = List.of(new Site("cloud-1", "https://team.atlassian.net", "Team site"));

    public FakeAtlassian() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        server.createContext("/ex/jira/", this::search);
        server.createContext("/oauth/token/accessible-resources", this::resources);
        server.createContext("/oauth/token", this::token);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void reset() {
        generation.set(0);
        liveRefreshTokens.clear();
        liveAccessTokens.clear();
        requests.clear();
        refreshCalls.set(0);
        expiresInSeconds = 3600;
        refreshDelayMillis = 0;
        tokenEndpointFailure = 0;
        searchFailure = 0;
        issueFailure = 0;
        issueCalls.set(0);
        searchCalls.set(0);
        searchBodies.clear();
        issues = List.of(new Issue("10001", "PROJ-1", "First task"), new Issue("10002", "PROJ-2", "Second task"));
        sites = List.of(new Site("cloud-1", "https://team.atlassian.net", "Team site"));
    }

    public List<String> searchBodies() {
        return searchBodies;
    }

    public List<String> requests() {
        return requests;
    }

    /** Makes the access token the fake has issued stop working, as if it expired at Atlassian. */
    public void revokeAccessTokens() {
        liveAccessTokens.clear();
    }

    private void token(HttpExchange exchange) throws IOException {
        var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add("POST /oauth/token");
        if (tokenEndpointFailure != 0) {
            respond(exchange, tokenEndpointFailure, "{\"error\":\"server_error\"}");
            return;
        }
        if (body.contains("\"grant_type\":\"authorization_code\"")) {
            if (body.contains("\"code\":\"good-code")) {
                respond(exchange, 200, issue());
            } else {
                respond(exchange, 403, "{\"error\":\"invalid_grant\"}");
            }
            return;
        }
        refreshCalls.incrementAndGet();
        sleep(refreshDelayMillis);
        var used = body.replaceAll(".*\"refresh_token\":\"([^\"]+)\".*", "$1");
        if (liveRefreshTokens.remove(used)) {
            respond(exchange, 200, issue());
        } else {
            respond(exchange, 403, "{\"error\":\"invalid_grant\"}");
        }
    }

    private void search(HttpExchange exchange) throws IOException {
        var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        var path = exchange.getRequestURI().getPath();
        if (path.contains("/rest/api/3/issue/")) {
            issue(exchange, path);
            return;
        }
        requests.add("POST " + path);
        searchCalls.incrementAndGet();
        searchBodies.add(body);
        var header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !liveAccessTokens.contains(header.replace("Bearer ", ""))) {
            respond(exchange, 401, "{\"message\":\"Unauthorized\"}");
            return;
        }
        if (searchFailure == 429) exchange.getResponseHeaders().add("Retry-After", "3");
        if (searchFailure != 0) {
            respond(exchange, searchFailure, "{}");
            return;
        }
        int offset = body.contains("\"nextPageToken\":\"page-") ? Integer.parseInt(body.replaceAll("(?s).*\"nextPageToken\":\"page-([0-9]+)\".*", "$1")) : 0;
        int size = Integer.parseInt(body.replaceAll("(?s).*\"maxResults\":([0-9]+).*", "$1"));
        var page = issues.subList(Math.min(offset, issues.size()), Math.min(offset + size, issues.size()));
        var json = new StringBuilder("{\"issues\":[");
        for (var issue : page) {
            if (json.charAt(json.length() - 1) != '[') json.append(',');
            json.append("{\"id\":\"").append(issue.id()).append("\",\"key\":\"").append(issue.key())
                    .append("\",\"fields\":{\"summary\":\"").append(issue.summary())
                    .append("\",\"status\":{\"name\":\"To Do\"},\"issuetype\":{\"name\":\"Task\"},\"project\":{\"key\":\"PROJ\"}}}");
        }
        json.append("]");
        if (offset + size < issues.size()) json.append(",\"nextPageToken\":\"page-").append(offset + size).append("\"");
        respond(exchange, 200, json.append("}").toString());
    }

    private void issue(HttpExchange exchange, String path) throws IOException {
        requests.add("GET " + path);
        issueCalls.incrementAndGet();
        var header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !liveAccessTokens.contains(header.replace("Bearer ", ""))) {
            respond(exchange, 401, "{}");
            return;
        }
        if (issueFailure != 0) {
            if (issueFailure == 429) exchange.getResponseHeaders().add("Retry-After", "3");
            respond(exchange, issueFailure, "{}");
            return;
        }
        var id = path.substring(path.lastIndexOf('/') + 1);
        var found = issues.stream().filter(candidate -> candidate.id().equals(id)).findFirst();
        if (found.isEmpty()) {
            respond(exchange, 404, "{}");
            return;
        }
        respond(exchange, 200, "{\"id\":\"" + found.get().id() + "\",\"key\":\"" + found.get().key() + "\",\"fields\":{\"summary\":\""
                + found.get().summary() + "\",\"status\":{\"name\":\"To Do\"},\"issuetype\":{\"name\":\"Task\"},\"project\":{\"key\":\"PROJ\"}}}");
    }

    private void resources(HttpExchange exchange) throws IOException {
        requests.add("GET /oauth/token/accessible-resources");
        var header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !liveAccessTokens.contains(header.replace("Bearer ", ""))) {
            respond(exchange, 401, "{\"message\":\"Unauthorized\"}");
            return;
        }
        var json = new StringBuilder("[");
        for (var site : sites) {
            if (json.length() > 1) json.append(',');
            json.append("{\"id\":\"").append(site.id()).append("\",\"url\":\"").append(site.url())
                    .append("\",\"name\":\"").append(site.name()).append("\",\"scopes\":[\"read:jira-work\"]}");
        }
        respond(exchange, 200, json.append(']').toString());
    }

    private String issue() {
        int n = generation.incrementAndGet();
        liveRefreshTokens.add("refresh-" + n);
        liveAccessTokens.add("access-" + n);
        return "{\"access_token\":\"access-" + n + "\",\"refresh_token\":\"refresh-" + n + "\",\"expires_in\":" + expiresInSeconds
                + ",\"scope\":\"read:jira-work offline_access\",\"token_type\":\"Bearer\"}";
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void sleep(long millis) {
        if (millis <= 0) return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
