package pl.macieserafin.hourforge.jira;

import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Builds the JQL sent to Jira from a fixed set of filters. User text never becomes JQL syntax: it is reduced to
 * letters, digits and a few separators and always placed inside quotes, so it cannot add clauses or functions.
 */
public final class JiraJql {
    public enum Assignee { ME, ANYONE }

    public enum SprintFilter { ANY, ACTIVE, CLOSED }

    public record Query(Assignee assignee, SprintFilter sprint, boolean includeDone, String projectKey, String text) {
    }

    public static final int MAX_TEXT = 100;
    private static final Pattern PROJECT_KEY = Pattern.compile("[A-Z][A-Z0-9_]{1,19}");
    private static final Pattern ISSUE_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_]{1,19}-[0-9]{1,9}");
    private static final Pattern UNSAFE = Pattern.compile("[^\\p{L}\\p{N} _.-]");

    private JiraJql() {
    }

    public static boolean validProjectKey(String key) {
        return key != null && PROJECT_KEY.matcher(key).matches();
    }

    /** Only safe characters, single spaces, at most {@link #MAX_TEXT} characters; empty when nothing usable is left. */
    public static String sanitizeText(String text) {
        if (text == null) return "";
        var cleaned = UNSAFE.matcher(text).replaceAll(" ").replaceAll("\\s+", " ").strip();
        return cleaned.length() > MAX_TEXT ? cleaned.substring(0, MAX_TEXT).strip() : cleaned;
    }

    public static String build(Query query) {
        var clauses = new ArrayList<String>();
        boolean bounded = false;
        if (query.assignee() == Assignee.ME) {
            clauses.add("assignee = currentUser()");
            bounded = true;
        }
        if (!query.includeDone()) clauses.add("statusCategory != Done");
        if (query.projectKey() != null && !query.projectKey().isBlank()) {
            if (!validProjectKey(query.projectKey())) throw new IllegalArgumentException("Invalid project key");
            clauses.add("project = \"" + query.projectKey() + "\"");
            bounded = true;
        }
        if (query.sprint() == SprintFilter.ACTIVE) {
            clauses.add("sprint in openSprints()");
            bounded = true;
        } else if (query.sprint() == SprintFilter.CLOSED) {
            clauses.add("sprint in closedSprints()");
            bounded = true;
        }
        var text = sanitizeText(query.text());
        if (!text.isEmpty()) {
            clauses.add(textClause(text));
            bounded = true;
        }
        // Jira refuses searches without any restriction, so a wide search is limited to recent work.
        if (!bounded) clauses.add("updated >= -30d");
        return String.join(" AND ", clauses) + " ORDER BY updated DESC";
    }

    private static String textClause(String text) {
        if (ISSUE_KEY.matcher(text).matches()) {
            var key = text.toUpperCase(Locale.ROOT);
            return "(key = \"" + key + "\" OR text ~ \"" + text + "\")";
        }
        var terms = new ArrayList<String>();
        for (var term : text.split(" ")) terms.add(term + "*");
        return "text ~ \"" + String.join(" ", terms) + "\"";
    }
}
