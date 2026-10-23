package pl.macieserafin.hourforge.session;

import jakarta.servlet.http.HttpServletRequest;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

/**
 * What a person needs to recognise a device: the browser, the system, a shortened address and an approximate place.
 * The full user agent and the full address are not kept. Null parts are shown as unknown.
 */
public record DeviceInfo(String browser, String system, String address, String location) implements Serializable {
    private static final long serialVersionUID = 1L;

    /** The device of the request; the place is looked up from the full address before it is shortened. */
    public static DeviceInfo of(HttpServletRequest request, GeoLocator locator) {
        var userAgent = request.getHeader("User-Agent");
        var address = request.getRemoteAddr();
        return new DeviceInfo(browserOf(userAgent), systemOf(userAgent), maskAddress(address), locator.locate(address));
    }

    static String browserOf(String userAgent) {
        if (userAgent == null) return null;
        // Order matters: Edge and Opera also say Chrome, and Chrome also says Safari.
        if (userAgent.contains("Edg/") || userAgent.contains("EdgA/") || userAgent.contains("EdgiOS/")) return "Edge";
        if (userAgent.contains("OPR/") || userAgent.contains("Opera")) return "Opera";
        if (userAgent.contains("Firefox/") || userAgent.contains("FxiOS/")) return "Firefox";
        if (userAgent.contains("SamsungBrowser/")) return "Samsung Internet";
        if (userAgent.contains("Chrome/") || userAgent.contains("CriOS/")) return "Chrome";
        if (userAgent.contains("Safari/") && userAgent.contains("Version/")) return "Safari";
        return null;
    }

    static String systemOf(String userAgent) {
        if (userAgent == null) return null;
        if (userAgent.contains("iPhone") || userAgent.contains("iPad")) return "iOS";
        if (userAgent.contains("Android")) return "Android";
        if (userAgent.contains("Windows")) return "Windows";
        if (userAgent.contains("CrOS")) return "ChromeOS";
        if (userAgent.contains("Mac OS X") || userAgent.contains("Macintosh")) return "macOS";
        if (userAgent.contains("Linux")) return "Linux";
        return null;
    }

    /** Keeps the network part only: 83.24.x.x for IPv4, the first 32 bits for IPv6. */
    static String maskAddress(String address) {
        return parse(address).map(bytes -> bytes.length == 4
                ? (bytes[0] & 0xff) + "." + (bytes[1] & 0xff) + ".x.x"
                : Integer.toHexString(((bytes[0] & 0xff) << 8) | (bytes[1] & 0xff)) + ":"
                        + Integer.toHexString(((bytes[2] & 0xff) << 8) | (bytes[3] & 0xff)) + ":x:x::").orElse(null);
    }

    /** A literal IPv4 or IPv6 address; anything else (a host name included) is refused, so DNS is never asked. */
    static Optional<InetAddress> literal(String address) {
        if (address == null || address.isBlank()) return Optional.empty();
        var ipv4 = address.matches("\\d{1,3}(\\.\\d{1,3}){3}");
        var ipv6 = address.contains(":") && address.matches("[0-9A-Fa-f:.]+");
        if (!ipv4 && !ipv6) return Optional.empty();
        try {
            return Optional.of(InetAddress.getByName(address));
        } catch (UnknownHostException exception) {
            return Optional.empty();
        }
    }

    private static Optional<byte[]> parse(String address) {
        return literal(address).map(InetAddress::getAddress);
    }
}
