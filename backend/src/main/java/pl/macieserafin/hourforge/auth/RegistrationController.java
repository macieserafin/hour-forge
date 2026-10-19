package pl.macieserafin.hourforge.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.macieserafin.hourforge.security.RequestRateLimits;

@RestController
public class RegistrationController {
    private final RegistrationService registration;
    private final RequestRateLimits rateLimits;

    public RegistrationController(RegistrationService registration, RequestRateLimits rateLimits) {
        this.registration = registration;
        this.rateLimits = rateLimits;
    }

    @PostMapping("/api/auth/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegistrationRequest request, HttpServletRequest http) {
        rateLimits.registration(http.getRemoteAddr());
        registration.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
