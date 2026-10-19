package pl.macieserafin.hourforge.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RegistrationController {
    private final RegistrationService registration;

    public RegistrationController(RegistrationService registration) { this.registration = registration; }

    @PostMapping("/api/auth/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegistrationRequest request) {
        registration.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
