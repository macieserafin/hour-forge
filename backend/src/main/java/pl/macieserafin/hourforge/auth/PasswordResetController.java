package pl.macieserafin.hourforge.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/password-reset")
public class PasswordResetController {
    private final PasswordResetService reset;

    public PasswordResetController(PasswordResetService reset) {
        this.reset = reset;
    }

    @PostMapping("/request")
    public ResponseEntity<Void> request(@RequestBody ResetRequest request, HttpServletRequest http) {
        reset.request(request.email(), http.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@RequestBody ConfirmRequest request) {
        reset.confirm(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    public record ResetRequest(String email) {
    }

    public record ConfirmRequest(@JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String token,
                                 @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String newPassword) {
        @Override
        public String toString() {
            return "ConfirmRequest[redacted]";
        }
    }
}
