package pl.macieserafin.hourforge.preferences;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.macieserafin.hourforge.security.AppUserPrincipal;

@RestController
@RequestMapping("/api/account/preferences")
public class PreferencesController {
    private final PreferencesService preferences;

    public PreferencesController(PreferencesService preferences) {
        this.preferences = preferences;
    }

    @GetMapping
    public PreferencesResponse get(@AuthenticationPrincipal AppUserPrincipal principal) {
        return preferences.get(principal.getUserId());
    }

    @PutMapping
    public PreferencesResponse save(@AuthenticationPrincipal AppUserPrincipal principal, @Valid @RequestBody PreferencesRequest request) {
        return preferences.save(principal.getUserId(), request);
    }
}
