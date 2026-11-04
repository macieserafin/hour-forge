package pl.macieserafin.hourforge.google.unit;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import pl.macieserafin.hourforge.google.GoogleIntent;

import static org.assertj.core.api.Assertions.assertThat;

/** The server-side check of the return address, the twin of the frontend's {@code safeReturnTo}. */
class GoogleReturnToTest {
    @ParameterizedTest
    @CsvSource({"/dashboard", "/teams", "/projects", "/time", "/integrations", "/whats-new", "/invite/abc_DEF-123", "/time?date=2026-11-04"})
    void theAppsOwnPagesAndInvitationsAreKept(String returnTo) {
        assertThat(GoogleIntent.safeReturnTo(returnTo)).isEqualTo(returnTo);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"https://evil.example/dashboard", "//evil.example", "/\\evil.example", "dashboard", "/account", "/operator",
            "/invite/", "/invite/../operator", "/invite/a/b", "/dashboard#fragment", "/time?x=<script>", "/time?next=//evil.example",
            "/DASHBOARD", " /dashboard", "javascript:alert(1)"})
    void anythingElseBecomesTheDashboard(String returnTo) {
        assertThat(GoogleIntent.safeReturnTo(returnTo)).isEqualTo("/dashboard");
    }

    @org.junit.jupiter.api.Test
    void anOverlongAddressBecomesTheDashboard() {
        assertThat(GoogleIntent.safeReturnTo("/invite/" + "a".repeat(255))).isEqualTo("/invite/" + "a".repeat(255));
        assertThat(GoogleIntent.safeReturnTo("/invite/" + "a".repeat(256))).isEqualTo("/dashboard");
        assertThat(GoogleIntent.safeReturnTo("/time?" + "a".repeat(600))).isEqualTo("/dashboard");
    }
}
