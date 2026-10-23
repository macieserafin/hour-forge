package pl.macieserafin.hourforge.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.macieserafin.hourforge.security.BotCheck;
import pl.macieserafin.hourforge.security.RequestRateLimits;

import java.util.Map;

@RestController
public class RegistrationController {
    private final RegistrationService registration;
    private final RequestRateLimits rateLimits;
    private final BotCheck botCheck;

    public RegistrationController(RegistrationService registration, RequestRateLimits rateLimits, BotCheck botCheck) {
        this.registration = registration;
        this.rateLimits = rateLimits;
        this.botCheck = botCheck;
    }

    @PostMapping("/api/auth/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegistrationRequest request, HttpServletRequest http) {
        rateLimits.registration(http.getRemoteAddr());
        if (!botCheck.passes(request.botCheckToken(), http.getRemoteAddr())) {
            return ResponseEntity.badRequest().body(Map.of("code", "BOT_CHECK_FAILED"));
        }
        registration.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
