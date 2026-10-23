package pl.macieserafin.hourforge.session;

import jakarta.servlet.http.HttpServletRequest;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * What a person needs to recognise a device: the browser, the system and a shortened address. The full user agent
 * and the full address are not kept. Null parts are shown as unknown.
 */
public record DeviceInfo(String browser, String system, String address) implements Serializable {
    private static final long serialVersionUID = 1L;

    public static DeviceInfo of(HttpServletRequest request) {
        var userAgent = request.getHeader("User-Agent");
        return new DeviceInfo(browserOf(userAgent), systemOf(userAgent), maskAddress(request.getRemoteAddr()));
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
        if (address == null || address.isBlank()) return null;
        // Only literal addresses reach InetAddress, so it never asks DNS: dotted IPv4, or IPv6 with a colon.
        var ipv4 = address.matches("\\d{1,3}(\\.\\d{1,3}){3}");
        var ipv6 = address.contains(":") && address.matches("[0-9A-Fa-f:.]+");
        if (!ipv4 && !ipv6) return null;
        try {
            var bytes = InetAddress.getByName(address).getAddress();
            if (bytes.length == 4) return (bytes[0] & 0xff) + "." + (bytes[1] & 0xff) + ".x.x";
            return Integer.toHexString(((bytes[0] & 0xff) << 8) | (bytes[1] & 0xff)) + ":"
                    + Integer.toHexString(((bytes[2] & 0xff) << 8) | (bytes[3] & 0xff)) + ":x:x::";
        } catch (UnknownHostException exception) {
            return null;
        }
    }
}
