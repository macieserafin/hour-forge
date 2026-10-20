package pl.macieserafin.hourforge.jira;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JiraJqlTest {
    private static JiraJql.Query query(JiraJql.Assignee assignee, JiraJql.SprintFilter sprint, boolean done, String project, String text) {
        return new JiraJql.Query(assignee, sprint, done, project, text);
    }

    @Test
    void myOpenIssuesAreTheDefault() {
        assertThat(JiraJql.build(query(JiraJql.Assignee.ME, JiraJql.SprintFilter.ANY, false, null, null)))
                .isEqualTo("assignee = currentUser() AND statusCategory != Done ORDER BY updated DESC");
    }

    @Test
    void sprintFiltersUseOnlyTheBuiltInFunctions() {
        assertThat(JiraJql.build(query(JiraJql.Assignee.ANYONE, JiraJql.SprintFilter.ACTIVE, false, null, null)))
                .contains("sprint in openSprints()").doesNotContain("updated >=");
        assertThat(JiraJql.build(query(JiraJql.Assignee.ANYONE, JiraJql.SprintFilter.CLOSED, true, null, null)))
                .contains("sprint in closedSprints()").doesNotContain("statusCategory");
    }

    @Test
    void aSearchWithoutAnyRestrictionIsLimitedToRecentWork() {
        assertThat(JiraJql.build(query(JiraJql.Assignee.ANYONE, JiraJql.SprintFilter.ANY, true, null, null)))
                .isEqualTo("updated >= -30d ORDER BY updated DESC");
    }

    @Test
    void textBecomesQuotedPrefixTerms() {
        assertThat(JiraJql.build(query(JiraJql.Assignee.ME, JiraJql.SprintFilter.ANY, false, null, "login  bug")))
                .contains("text ~ \"login* bug*\"");
    }

    @Test
    void anIssueKeyAlsoMatchesByKey() {
        assertThat(JiraJql.build(query(JiraJql.Assignee.ME, JiraJql.SprintFilter.ANY, false, null, "proj-12")))
                .contains("(key = \"PROJ-12\" OR text ~ \"proj-12\")");
    }

    @ParameterizedTest
    @ValueSource(strings = {"x\" OR project is not EMPTY --", "a\\\" ) OR (1=1", "x) ORDER BY created", "abc\"; DROP", "'quoted' \\ back"})
    void textCannotBreakOutOfItsQuotes(String hostile) {
        var jql = JiraJql.build(query(JiraJql.Assignee.ME, JiraJql.SprintFilter.ANY, false, null, hostile));
        var text = jql.substring(jql.indexOf("text ~ \"") + 8, jql.indexOf("\" ORDER BY"));
        assertThat(text).doesNotContain("\"").doesNotContain("\\").doesNotContain("(").doesNotContain(")");
        assertThat(jql).endsWith(" ORDER BY updated DESC");
        assertThat(jql.split("ORDER BY", -1)).hasSize(2);
    }

    @Test
    void sanitizingKeepsLettersOfAnyLanguageAndCapsTheLength() {
        assertThat(JiraJql.sanitizeText("  Zażółć gęślą jaźń  ")).isEqualTo("Zażółć gęślą jaźń");
        assertThat(JiraJql.sanitizeText("a".repeat(500))).hasSize(JiraJql.MAX_TEXT);
        assertThat(JiraJql.sanitizeText("\"\\()")).isEmpty();
        assertThat(JiraJql.sanitizeText(null)).isEmpty();
    }

    @Test
    void projectKeysMustLookLikeProjectKeys() {
        assertThat(JiraJql.build(query(JiraJql.Assignee.ANYONE, JiraJql.SprintFilter.ANY, false, "PROJ_1", null))).contains("project = \"PROJ_1\"");
        for (var bad : new String[] {"proj", "P", "PROJ\" OR 1=1", "A B", "1ABC"}) {
            assertThat(JiraJql.validProjectKey(bad)).isFalse();
            assertThatThrownBy(() -> JiraJql.build(query(JiraJql.Assignee.ANYONE, JiraJql.SprintFilter.ANY, false, bad, null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
