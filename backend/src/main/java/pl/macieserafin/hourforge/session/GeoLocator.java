package pl.macieserafin.hourforge.session;

import com.maxmind.db.Reader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The approximate place of an address ("Warsaw, Poland") from a local IP location database in the MaxMind format,
 * such as DB-IP Lite (CC BY 4.0) or GeoLite2. Off when no file is configured; nothing is ever sent to another server.
 */
@Component
public class GeoLocator implements DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(GeoLocator.class);

    private final Reader reader;
    private final String attribution;

    public GeoLocator(@Value("${app.geoip.database:}") String database, @Value("${app.geoip.attribution:}") String attribution) {
        this.reader = open(database);
        this.attribution = reader == null || attribution.isBlank() ? null : attribution;
    }

    /** "City, Country", or only the country, or null when unknown or when no database is configured. */
    public String locate(String address) {
        if (reader == null) return null;
        var literal = DeviceInfo.literal(address);
        if (literal.isEmpty()) return null;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> record = reader.get(literal.get(), Map.class);
            if (record == null) return null;
            var place = Stream.of(name(record.get("city")), name(record.get("country")))
                    .filter(Objects::nonNull).collect(Collectors.joining(", "));
            return place.isEmpty() ? null : place.length() > 160 ? place.substring(0, 160) : place;
        } catch (IOException | RuntimeException exception) {
            // An IPv6 address in an IPv4-only database, or a damaged file: the place is simply unknown.
            return null;
        }
    }

    /** The credit the database licence asks for, shown next to the places; null when places are off. */
    public String attribution() {
        return attribution;
    }

    private static String name(Object part) {
        if (!(part instanceof Map<?, ?> map) || !(map.get("names") instanceof Map<?, ?> names)) return null;
        var name = names.get("pl") != null ? names.get("pl") : names.get("en");
        return name instanceof String text && !text.isBlank() ? text : null;
    }

    private static Reader open(String database) {
        if (database == null || database.isBlank()) return null;
        try {
            return new Reader(new File(database));
        } catch (IOException exception) {
            log.warn("IP location database could not be opened; places are not shown: {}", exception.getClass().getSimpleName());
            return null;
        }
    }

    @Override
    public void destroy() throws IOException {
        if (reader != null) reader.close();
    }
}
