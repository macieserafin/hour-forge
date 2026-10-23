package pl.macieserafin.hourforge.session;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.macieserafin.hourforge.auth.AccountMailService;
import pl.macieserafin.hourforge.user.UserRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Sign-ins of each account: shown to the person, and the reason for an e-mail when an account is used from a new
 * kind of device. Failed attempts are recorded only for existing accounts; the rate limits bound how many there are.
 */
@Service
public class LoginHistory {
    static final int SHOWN = 50;

    public record Entry(Instant occurredAt, boolean successful, String browser, String system, String address, String location) {
    }

    /** The latest sign-ins and, when places are shown, the credit for the location data. */
    public record History(List<Entry> entries, String locationSource) {
    }

    private final LoginEventRepository events;
    private final UserRepository users;
    private final AccountMailService mail;
    private final GeoLocator locator;
    private final Clock clock;

    public LoginHistory(LoginEventRepository events, UserRepository users, AccountMailService mail, GeoLocator locator, Clock clock) {
        this.events = events;
        this.users = users;
        this.mail = mail;
        this.locator = locator;
        this.clock = clock;
    }

    /**
     * Records a sign-in and tells the person by e-mail when the browser and system were not seen before. The very
     * first sign-in of an account is not news, so it sends nothing.
     */
    @Transactional
    public void recordSuccess(UUID userId, DeviceInfo device) {
        var now = clock.instant();
        var firstEver = !events.existsByUserIdAndOutcome(userId, LoginEvent.Outcome.SUCCESS);
        var known = !firstEver && events.signedInBeforeFrom(userId, device.browser(), device.system());
        events.save(new LoginEvent(userId, now, LoginEvent.Outcome.SUCCESS, device));
        if (!firstEver && !known) users.findById(userId).ifPresent(user -> mail.sendNewDeviceSignIn(user, device, now));
    }

    /** Records a failed attempt on an existing account; attempts on unknown addresses are not stored. */
    @Transactional
    public void recordFailure(String email, DeviceInfo device) {
        if (email == null || email.isBlank() || email.length() > 320) return;
        users.findByEmailIgnoreCase(email.trim())
                .filter(user -> user.getDeletedAt() == null)
                .ifPresent(user -> events.save(new LoginEvent(user.getId(), clock.instant(), LoginEvent.Outcome.FAILED, device)));
    }

    @Transactional(readOnly = true)
    public History list(UUID userId) {
        var entries = events.findByUserIdOrderByOccurredAtDesc(userId, PageRequest.of(0, SHOWN)).stream()
                .map(event -> new Entry(event.getOccurredAt(), event.getOutcome() == LoginEvent.Outcome.SUCCESS,
                        event.getBrowser(), event.getSystem(), event.getAddress(), event.getLocation()))
                .toList();
        return new History(entries, locator.attribution());
    }
}
