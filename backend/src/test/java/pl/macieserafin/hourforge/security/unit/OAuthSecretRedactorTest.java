package pl.macieserafin.hourforge.security.unit;

import org.junit.jupiter.api.Test;
import pl.macieserafin.hourforge.security.OAuthSecretRedactor;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthSecretRedactorTest {
    @Test
    void theCallbackParametersAreRedacted() {
        var line = "GET /api/auth/oauth2/callback/google?state=abc-DEF_123&code=4%2F0AfJohXn&scope=email+openid failed";
        assertThat(OAuthSecretRedactor.redact(line))
                .isEqualTo("GET /api/auth/oauth2/callback/google?state=[REDACTED]&code=[REDACTED]&scope=email+openid failed");
    }

    @Test
    void tokensInFormsJsonAndHeadersAreRedacted() {
        assertThat(OAuthSecretRedactor.redact("grant_type=authorization_code&code_verifier=xyz&client_secret=s3cr3t"))
                .isEqualTo("grant_type=authorization_code&code_verifier=[REDACTED]&client_secret=[REDACTED]");
        assertThat(OAuthSecretRedactor.redact("{\"access_token\":\"ya29.a0\",\"id_token\" : \"eyJhbGciOi.x.y\",\"expires_in\":3599}"))
                .isEqualTo("{\"access_token\":\"[REDACTED]\",\"id_token\":\"[REDACTED]\",\"expires_in\":3599}");
        assertThat(OAuthSecretRedactor.redact("Authorization: Bearer ya29.a0AfB_byC-1==")).isEqualTo("Authorization: Bearer [REDACTED]");
    }

    @Test
    void wordsThatOnlyEndLikeAParameterAreKept() {
        var line = "errorCode=invalid_nonce zipcode=00-001 Sign-in with Google did not finish: invalid_nonce";
        assertThat(OAuthSecretRedactor.redact(line)).isEqualTo(line);
    }
}
