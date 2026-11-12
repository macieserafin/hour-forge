package pl.macieserafin.hourforge.config.integration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import pl.macieserafin.hourforge.support.TestData;
import pl.macieserafin.hourforge.support.TestMailConfig;

import static org.assertj.core.api.Assertions.assertThat;

/** Health and metrics answer only on the management port, which the proxy never routes to; the API port has none. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
@ActiveProfiles("test")
@Import({TestData.class, TestMailConfig.class})
class ManagementPortIT {
    private final HttpClient http = HttpClient.newHttpClient();
    @LocalServerPort int apiPort;
    @LocalManagementPort int managementPort;

    @Test
    void healthAndMetricsAnswerOnTheManagementPortWithoutSignIn() throws Exception {
        var readiness = get(managementPort, "/actuator/health/readiness");
        assertThat(readiness.statusCode()).isEqualTo(200);
        assertThat(readiness.body()).contains("\"UP\"").doesNotContain("details");
        assertThat(get(managementPort, "/actuator/health/liveness").statusCode()).isEqualTo(200);
        // HTTP metrics exist once the API has answered something.
        get(apiPort, "/api/config");
        var metrics = get(managementPort, "/actuator/prometheus");
        assertThat(metrics.statusCode()).isEqualTo(200);
        assertThat(metrics.body()).contains("http_server_requests", "hikaricp_connections");
    }

    @Test
    void theApiPortServesNoManagementEndpoints() throws Exception {
        for (var path : new String[] {"/actuator/health", "/actuator/prometheus", "/actuator/env", "/actuator"}) {
            assertThat(get(apiPort, path).statusCode()).as(path).isIn(401, 404);
        }
        assertThat(get(managementPort, "/actuator/env").statusCode()).isIn(401, 404);
    }

    @Test
    void everyAnswerCarriesARequestIdAndKeepsAValidOneFromTheProxy() throws Exception {
        var generated = get(apiPort, "/api/config");
        assertThat(generated.headers().firstValue("X-Request-Id")).hasValueSatisfying(id -> assertThat(id).hasSize(36));
        var kept = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + apiPort + "/api/config"))
                .header("X-Request-Id", "caddy-1234-abcd").build(), HttpResponse.BodyHandlers.ofString());
        assertThat(kept.headers().firstValue("X-Request-Id")).contains("caddy-1234-abcd");
        var replaced = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + apiPort + "/api/config"))
                .header("X-Request-Id", "bad id with spaces").build(), HttpResponse.BodyHandlers.ofString());
        assertThat(replaced.headers().firstValue("X-Request-Id")).hasValueSatisfying(id -> assertThat(id).hasSize(36));
    }

    private HttpResponse<String> get(int port, String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
