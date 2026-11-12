package pl.macieserafin.hourforge.account;

import java.io.OutputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes the copy of a person's data straight into the response. The time entries, which can be hundreds of thousands,
 * are read with a database cursor and written one by one, so memory does not grow with their number; the rest is small
 * and comes from {@link AccountDataService#exportWithoutEntries}. The JSON has the shape of {@link AccountExport}.
 */
@Service
public class AccountExportWriter {
    /** Rows fetched from the cursor at a time. */
    static final int FETCH_SIZE = 500;
    private static final String ENTRIES = """
            select e.id, e.team_id, t.name as team_name, e.project_id, p.name as project_name, e.entry_type, e.work_date,
                   e.started_at, e.ended_at, e.duration_seconds, e.paused_duration_micros, e.description, e.created_at,
                   e.updated_at, e.jira_issue_key, g.name as tag_name, e.last_edited_at, editor.display_name as last_edited_by
            from time_entries e
            join teams t on t.id = e.team_id
            join projects p on p.id = e.project_id
            left join tags g on g.id = e.tag_id
            left join users editor on editor.id = e.last_edited_by
            where e.user_id = ?
            order by e.created_at, e.id
            """;

    private final AccountDataService data;
    private final ObjectMapper json;
    private final JdbcTemplate cursor;

    public AccountExportWriter(AccountDataService data, ObjectMapper json, DataSource dataSource) {
        this.data = data;
        this.json = json;
        this.cursor = new JdbcTemplate(dataSource);
        // With a fetch size inside a transaction, PostgreSQL returns the rows in batches instead of all at once.
        this.cursor.setFetchSize(FETCH_SIZE);
    }

    /** Own entries from every team, including teams the person has left, as decided for former members. */
    @Transactional(readOnly = true)
    public void write(UUID userId, Instant exportedAt, OutputStream out) {
        var export = data.exportWithoutEntries(userId);
        try (JsonGenerator generator = json.createGenerator(out)) {
            generator.writeStartObject();
            field(generator, "exportedAt", exportedAt);
            field(generator, "profile", export.profile());
            field(generator, "teams", export.teams());
            generator.writeName("timeEntries");
            generator.writeStartArray();
            cursor.query(ENTRIES, rows -> {
                json.writeValue(generator, entry(rows));
            }, userId);
            generator.writeEndArray();
            field(generator, "preferences", export.preferences());
            field(generator, "logins", export.logins());
            field(generator, "templates", export.templates());
            field(generator, "jiraAuthorizations", export.jiraAuthorizations());
            field(generator, "twoFactor", export.twoFactor());
            field(generator, "signInMethods", export.signInMethods());
            generator.writeEndObject();
        }
    }

    private void field(JsonGenerator generator, String name, Object value) {
        generator.writeName(name);
        json.writeValue(generator, value);
    }

    private static AccountExport.Entry entry(ResultSet row) throws SQLException {
        return new AccountExport.Entry(row.getObject("id", UUID.class), row.getObject("team_id", UUID.class),
                row.getString("team_name"), row.getObject("project_id", UUID.class), row.getString("project_name"),
                row.getString("entry_type"), row.getObject("work_date", LocalDate.class), instant(row, "started_at"),
                instant(row, "ended_at"), (Integer) row.getObject("duration_seconds"),
                row.getLong("paused_duration_micros") / 1_000_000.0, row.getString("description"), instant(row, "created_at"),
                instant(row, "updated_at"), row.getString("jira_issue_key"), row.getString("tag_name"),
                instant(row, "last_edited_at"), row.getString("last_edited_by"));
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        var value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
