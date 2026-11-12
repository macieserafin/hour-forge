package pl.macieserafin.hourforge.config;

import java.util.Map;
import org.flywaydb.core.Flyway;

/**
 * Runs the database migrations as a deployment step ({@code java -jar app.jar migrate}), signed in as the migration
 * account that owns the schema. The application itself then runs with an account that can only read and write rows.
 * No Spring context is started, so mail, keys and sign-in providers need not be configured for this step.
 */
public final class DatabaseMigration {
    public static final String COMMAND = "migrate";

    private DatabaseMigration() {
    }

    /** Applies pending migrations; returns how many ran. Throws when the settings are missing or a migration fails. */
    public static int run(Map<String, String> env) {
        var username = required(env, "MIGRATION_DB_USERNAME");
        var password = required(env, "MIGRATION_DB_PASSWORD");
        var flyway = Flyway.configure()
                .dataSource(url(env), username, password)
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .load();
        return flyway.migrate().migrationsExecuted;
    }

    /** The same address the application uses (application.properties): DB_URL, or DB_HOST, DB_PORT and DB_NAME. */
    static String url(Map<String, String> env) {
        var url = env.get("DB_URL");
        if (url != null && !url.isBlank()) return url.strip();
        return "jdbc:postgresql://" + env.getOrDefault("DB_HOST", "localhost") + ":" + env.getOrDefault("DB_PORT", "55432")
                + "/" + env.getOrDefault("DB_NAME", "hourforge");
    }

    private static String required(Map<String, String> env, String name) {
        var value = env.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Set " + name + " to run the migrations.");
        return value;
    }
}
