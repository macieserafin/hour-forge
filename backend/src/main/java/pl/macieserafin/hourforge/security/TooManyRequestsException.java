package pl.macieserafin.hourforge.security;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

public class TooManyRequestsException extends ResponseStatusException {
    private final long retryAfterSeconds;

    public TooManyRequestsException(Duration retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS, "Too many requests");
        this.retryAfterSeconds = retryAfterSeconds(retryAfter);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    @Override
    public HttpHeaders getHeaders() {
        var headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        return headers;
    }

    public static long retryAfterSeconds(Duration retryAfter) {
        return Math.max(1, (retryAfter.toMillis() + 999) / 1000);
    }
}
