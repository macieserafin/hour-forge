package pl.macieserafin.hourforge.session.unit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import pl.macieserafin.hourforge.session.DeviceInfo;
import pl.macieserafin.hourforge.session.GeoLocator;
import pl.macieserafin.hourforge.support.TestMmdb;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeoLocatorTest {
    private static final String CREDIT = "IP Geolocation by DB-IP (https://db-ip.com)";

    // Not a @TempDir: the reader maps the file into memory, and Windows cannot delete a mapped file until it is collected.
    static final Path folder = Path.of("target", "geoip-test");
    static Path database;

    @BeforeAll
    static void writeDatabase() throws Exception {
        Files.createDirectories(folder);
        database = TestMmdb.write(folder.resolve("test.mmdb"), List.of(
                new TestMmdb.Place("83.24.0.0/16", "Warsaw", "Poland"),
                new TestMmdb.Place("91.0.0.0/8", null, "Germany")));
    }

    @Test
    void namesTheCityAndCountryOrOnlyTheCountry() throws Exception {
        try (var closing = new Closing(new GeoLocator(database.toString(), CREDIT))) {
            assertThat(closing.locator.locate("83.24.117.9")).isEqualTo("Warsaw, Poland");
            assertThat(closing.locator.locate("91.200.1.1")).isEqualTo("Germany");
            assertThat(closing.locator.locate("10.0.0.1")).isNull();
            assertThat(closing.locator.attribution()).isEqualTo(CREDIT);
        }
    }

    @Test
    void anUnusableAddressIsSimplyUnknown() throws Exception {
        try (var closing = new Closing(new GeoLocator(database.toString(), CREDIT))) {
            assertThat(closing.locator.locate("2a02:a311::1")).isNull();
            assertThat(closing.locator.locate("example.com")).isNull();
            assertThat(closing.locator.locate(null)).isNull();
        }
    }

    @Test
    void withoutADatabasePlacesAndTheCreditAreOff() {
        var off = new GeoLocator("", CREDIT);
        assertThat(off.locate("83.24.117.9")).isNull();
        assertThat(off.attribution()).isNull();
    }

    @Test
    void aMissingOrBrokenFileTurnsPlacesOffInsteadOfFailingTheStart() throws Exception {
        var broken = Files.writeString(folder.resolve("broken.mmdb"), "not a database");
        assertThat(new GeoLocator(broken.toString(), CREDIT).locate("83.24.117.9")).isNull();
        assertThat(new GeoLocator(folder.resolve("missing.mmdb").toString(), CREDIT).attribution()).isNull();
    }

    @Test
    void theDeviceGetsThePlaceFromTheFullAddressButKeepsOnlyTheShortenedOne() throws Exception {
        try (var closing = new Closing(new GeoLocator(database.toString(), CREDIT))) {
            var request = new MockHttpServletRequest();
            request.setRemoteAddr("83.24.117.9");
            var device = DeviceInfo.of(request, closing.locator);
            assertThat(device.address()).isEqualTo("83.24.x.x");
            assertThat(device.location()).isEqualTo("Warsaw, Poland");
        }
    }

    private record Closing(GeoLocator locator) implements AutoCloseable {
        @Override
        public void close() throws Exception {
            locator.destroy();
        }
    }
}
