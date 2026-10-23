package pl.macieserafin.hourforge.session.unit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import pl.macieserafin.hourforge.session.DeviceInfo;
import pl.macieserafin.hourforge.session.GeoLocator;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceInfoTest {
    private static final GeoLocator NO_PLACES = new GeoLocator("", "");

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36|Chrome|Windows",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0|Edge|Windows",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15|Safari|macOS",
            "Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0|Firefox|Linux",
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36|Chrome|Android",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/130.0 Mobile/15E148 Safari/604.1|Chrome|iOS",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 OPR/114.0.0.0|Opera|Windows",
            "Mozilla/5.0 (X11; CrOS x86_64 14541.0.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36|Chrome|ChromeOS",
    })
    void recognisesCommonBrowsersAndSystems(String userAgent, String browser, String system) {
        var device = DeviceInfo.of(request(userAgent, "10.0.0.1"), NO_PLACES);
        assertThat(device.browser()).isEqualTo(browser);
        assertThat(device.system()).isEqualTo(system);
    }

    @Test
    void anUnknownOrMissingUserAgentGivesNoGuess() {
        assertThat(DeviceInfo.of(request("curl/8.4.0", "10.0.0.1"), NO_PLACES)).extracting(DeviceInfo::browser, DeviceInfo::system)
                .containsExactly(null, null);
        assertThat(DeviceInfo.of(request(null, "10.0.0.1"), NO_PLACES)).extracting(DeviceInfo::browser, DeviceInfo::system)
                .containsExactly(null, null);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "83.24.117.9|83.24.x.x",
            "127.0.0.1|127.0.x.x",
            "2a02:a311:1234:5678::1|2a02:a311:x:x::",
            "::1|0:0:x:x::",
            "::ffff:83.24.117.9|83.24.x.x",
    })
    void keepsOnlyTheNetworkPartOfTheAddress(String address, String shown) {
        assertThat(DeviceInfo.of(request(null, address), NO_PLACES).address()).isEqualTo(shown);
    }

    @ParameterizedTest
    @CsvSource({"example.com", "not an address", "''"})
    void neverResolvesNamesOrKeepsGarbage(String address) {
        assertThat(DeviceInfo.of(request(null, address), NO_PLACES).address()).isNull();
    }

    private static MockHttpServletRequest request(String userAgent, String address) {
        var request = new MockHttpServletRequest();
        if (userAgent != null) request.addHeader("User-Agent", userAgent);
        request.setRemoteAddr(address);
        return request;
    }
}
