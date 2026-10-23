package pl.macieserafin.hourforge.session;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The last part of every successful sign-in, after the password and, when it is on, the second step: the session
 * gets its lifetime and device, and the sign-in is written to the history (which may send the new-device e-mail).
 */
@Component
public class SignInCompletion {
    private final DeviceSessions devices;
    private final LoginHistory logins;
    private final GeoLocator locator;

    public SignInCompletion(DeviceSessions devices, LoginHistory logins, GeoLocator locator) {
        this.devices = devices;
        this.logins = logins;
        this.locator = locator;
    }

    public void complete(HttpServletRequest request, UUID userId, boolean remembered) {
        if (remembered) {
            // "Zapamiętaj mnie": idle for up to 30 days, and a cookie that outlives the browser (see SessionConfig).
            request.getSession().setMaxInactiveInterval((int) SessionConfig.REMEMBER_FOR.toSeconds());
            request.setAttribute(SessionConfig.REMEMBER_ATTRIBUTE, Boolean.TRUE);
        }
        var device = DeviceInfo.of(request, locator);
        devices.recordSignIn(request, device, remembered);
        logins.recordSuccess(userId, device);
    }

    /** A wrong password for the given address (only existing accounts are recorded). */
    public void failPassword(HttpServletRequest request, String email) {
        logins.recordFailure(email, DeviceInfo.of(request, locator));
    }

    /** A wrong code in the second step, after a right password. */
    public void failSecondStep(HttpServletRequest request, UUID userId) {
        logins.recordFailure(userId, DeviceInfo.of(request, locator));
    }
}
