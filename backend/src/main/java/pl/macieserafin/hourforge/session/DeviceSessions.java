package pl.macieserafin.hourforge.session;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The signed-in devices of a person: one stored session each. A session is shown by a random handle, never by its
 * id, because the id is the cookie value.
 */
@Service
public class DeviceSessions {
    static final String DEVICE = DeviceSessions.class.getName() + ".DEVICE";
    static final String HANDLE = DeviceSessions.class.getName() + ".HANDLE";
    static final String SIGNED_IN_AT = DeviceSessions.class.getName() + ".SIGNED_IN_AT";
    static final String REMEMBERED = DeviceSessions.class.getName() + ".REMEMBERED";

    public record DeviceSession(String id, String browser, String system, String address, Instant signedInAt,
                                Instant lastActiveAt, boolean current, boolean remembered) {
    }

    private final FindByIndexNameSessionRepository<? extends Session> sessions;
    private final Clock clock;

    public DeviceSessions(FindByIndexNameSessionRepository<? extends Session> sessions, Clock clock) {
        this.sessions = sessions;
        this.clock = clock;
    }

    /** Called right after a successful sign-in, on the session that sign-in created. */
    public void recordSignIn(HttpServletRequest request, boolean remembered) {
        var session = request.getSession();
        session.setAttribute(DEVICE, DeviceInfo.of(request));
        session.setAttribute(HANDLE, UUID.randomUUID().toString());
        session.setAttribute(SIGNED_IN_AT, clock.instant());
        session.setAttribute(REMEMBERED, remembered);
    }

    public List<DeviceSession> list(UUID userId, String currentSessionId) {
        return sessionsOf(userId).stream()
                .map(session -> describe(session, session.getId().equals(currentSessionId)))
                .sorted(Comparator.comparing(DeviceSession::current).reversed()
                        .thenComparing(DeviceSession::lastActiveAt, Comparator.reverseOrder()))
                .toList();
    }

    /** Ends one other session of the person. The current one is ended by signing out. */
    public void end(UUID userId, String handle, String currentSessionId) {
        var session = sessionsOf(userId).stream()
                .filter(candidate -> handle.equals(candidate.getAttribute(HANDLE)))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (session.getId().equals(currentSessionId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sign out to end the current session");
        }
        sessions.deleteById(session.getId());
    }

    /** Removes the stored sessions of the person, except the given one (may be null). */
    public void endAllExcept(UUID userId, String keepSessionId) {
        sessionsOf(userId).stream()
                .filter(session -> !session.getId().equals(keepSessionId))
                .forEach(session -> sessions.deleteById(session.getId()));
    }

    private List<? extends Session> sessionsOf(UUID userId) {
        return sessions.findByPrincipalName(userId.toString()).values().stream()
                .filter(session -> !session.isExpired())
                .toList();
    }

    private static DeviceSession describe(Session session, boolean current) {
        DeviceInfo device = session.getAttribute(DEVICE);
        String handle = session.getAttribute(HANDLE);
        Instant signedInAt = session.getAttribute(SIGNED_IN_AT);
        Boolean remembered = session.getAttribute(REMEMBERED);
        return new DeviceSession(handle,
                device == null ? null : device.browser(), device == null ? null : device.system(),
                device == null ? null : device.address(),
                signedInAt == null ? session.getCreationTime() : signedInAt, session.getLastAccessedTime(), current,
                Boolean.TRUE.equals(remembered));
    }
}
