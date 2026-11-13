package pl.macieserafin.hourforge.mail.unit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import pl.macieserafin.hourforge.mail.Email;
import pl.macieserafin.hourforge.mail.SmtpEmailSender;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The SMTP settings of {@code application.properties} against a server that offers no STARTTLS (as an attacker who strips
 * it would look like): with the production default the SMTP password is never sent, and only the explicit local-test
 * switch {@code MAIL_STARTTLS=false} talks to such a server. The test profile keeps a developer's own {@code .env} out.
 */
class SmtpTlsPolicyTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class));
    private final List<String> received = new CopyOnWriteArrayList<>();
    private ServerSocket server;

    @BeforeEach
    void startServerWithoutStartTls() throws IOException {
        server = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
        var thread = new Thread(this::serve, "fake-smtp");
        thread.setDaemon(true);
        thread.start();
    }

    @AfterEach
    void stopServer() throws IOException {
        server.close();
    }

    @Test
    void byDefaultAServerWithoutStartTlsIsRefusedBeforeTheCredentialsAreSent() {
        runner.withPropertyValues(settings("true")).run(context -> {
            var sender = new SmtpEmailSender(context.getBean(JavaMailSender.class), "HourForge <no-reply@mail.hourforge.example>");

            assertThatThrownBy(() -> sender.deliver(email())).isInstanceOf(MailSendException.class)
                    .hasMessageContaining("STARTTLS is required");
            assertThat(received).noneMatch(line -> line.startsWith("AUTH") || line.startsWith("MAIL FROM"));
        });
    }

    @Test
    void withStartTlsSwitchedOffTheLocalTestServerIsUsedAsItIs() {
        runner.withPropertyValues(settings("false")).run(context -> {
            var sender = new SmtpEmailSender(context.getBean(JavaMailSender.class), "HourForge <no-reply@mail.hourforge.example>");

            sender.deliver(email());

            assertThat(received).anyMatch(line -> line.startsWith("MAIL FROM"));
        });
    }

    private String[] settings(String startTls) {
        return new String[]{"spring.profiles.active=test", "spring.mail.host=127.0.0.1",
                "spring.mail.port=" + server.getLocalPort(), "spring.mail.username=smtp-user",
                "spring.mail.password=smtp-secret", "MAIL_STARTTLS=" + startTls};
    }

    private static Email email() {
        return new Email("ala@example.test", "Subject", "text", "<p>html</p>", Email.Category.TRANSACTIONAL, null, null);
    }

    /** A bare SMTP server that answers EHLO with a plain list of extensions: AUTH, but no STARTTLS. */
    private void serve() {
        while (!server.isClosed()) {
            try (var socket = server.accept()) {
                talk(socket);
            } catch (IOException ignored) {
                // Closed by the client or by the test.
            }
        }
    }

    private void talk(Socket socket) throws IOException {
        var in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
        var out = socket.getOutputStream();
        reply(out, "220 fake ESMTP");
        String line;
        boolean data = false;
        while ((line = in.readLine()) != null) {
            if (data) {
                if (line.equals(".")) {
                    data = false;
                    reply(out, "250 queued");
                }
                continue;
            }
            received.add(line);
            if (line.startsWith("EHLO") || line.startsWith("HELO")) {
                reply(out, "250-fake\r\n250 AUTH PLAIN LOGIN");
            } else if (line.startsWith("AUTH")) {
                reply(out, "235 ok");
            } else if (line.equals("DATA")) {
                data = true;
                reply(out, "354 go");
            } else if (line.equals("QUIT")) {
                reply(out, "221 bye");
                return;
            } else {
                reply(out, "250 ok");
            }
        }
    }

    private static void reply(OutputStream out, String text) throws IOException {
        out.write((text + "\r\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }
}
