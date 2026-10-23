package pl.macieserafin.hourforge.security.unit;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.macieserafin.hourforge.security.TurnstileBotCheck;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks the real HTTP exchange with a local stand-in for Cloudflare's verification endpoint. */
class TurnstileBotCheckTest {
    private HttpServer server;
    private final AtomicReference<String> received = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String answer = "{\"success\":true}";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/siteverify", exchange -> {
            received.set(URLDecoder.decode(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), StandardCharsets.UTF_8));
            var bytes = answer.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private TurnstileBotCheck check(String siteKey, String secret) {
        return new TurnstileBotCheck(siteKey, secret, "http://127.0.0.1:" + server.getAddress().getPort() + "/siteverify");
    }

    @Test
    void sendsTheSecretTokenAndAddressAndTrustsOnlySuccess() {
        var check = check("site", "secret-key");
        assertThat(check.passes("token-1", "83.24.117.9")).isTrue();
        assertThat(received.get()).contains("secret=secret-key").contains("response=token-1").contains("remoteip=83.24.117.9");

        answer = "{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}";
        assertThat(check.passes("token-2", "83.24.117.9")).isFalse();
    }

    @Test
    void anUnreachableOrFailingServiceDoesNotLetTheRequestThrough() {
        status = 500;
        assertThat(check("site", "secret-key").passes("token", null)).isFalse();
        server.stop(0);
        assertThat(check("site", "secret-key").passes("token", null)).isFalse();
    }

    @Test
    void missingOrOverlongTokensAreRejectedWithoutAsking() {
        var check = check("site", "secret-key");
        assertThat(check.passes(null, null)).isFalse();
        assertThat(check.passes(" ", null)).isFalse();
        assertThat(check.passes("x".repeat(2049), null)).isFalse();
        assertThat(received.get()).isNull();
    }

    @Test
    void withoutBothKeysTheCheckIsOff() {
        assertThat(check("", "secret-key").siteKey()).isNull();
        assertThat(check("site", "").passes(null, null)).isTrue();
        assertThat(check("site", "secret-key").siteKey()).isEqualTo("site");
        assertThat(received.get()).isNull();
    }
}
