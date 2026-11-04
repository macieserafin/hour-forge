package pl.macieserafin.hourforge.google;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** A refused link or unlink, answered as {@code {"code": ...}} with its status. */
public class GoogleLinkException extends ResponseStatusException {
    public static final String ALREADY_LINKED_TO_YOU = "GOOGLE_ALREADY_LINKED_TO_YOU";
    public static final String LAST_SIGN_IN_METHOD = "LAST_SIGN_IN_METHOD";

    private final String code;

    GoogleLinkException(HttpStatus status, String code) {
        super(status, code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
