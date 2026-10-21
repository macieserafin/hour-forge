package pl.macieserafin.hourforge.report;

public enum ReportGrouping {
    DAY(false, false, true, false, false), MEMBER(true, false, false, false, false), PROJECT(false, true, false, false, false),
    MEMBER_PROJECT(true, true, false, false, false), DAY_MEMBER(true, false, true, false, false), DAY_PROJECT(false, true, true, false, false),
    TAG(false, false, false, true, false), JIRA_ISSUE(false, false, false, false, true);

    final boolean member;
    final boolean project;
    final boolean day;
    final boolean tag;
    final boolean issue;

    ReportGrouping(boolean member, boolean project, boolean day, boolean tag, boolean issue) {
        this.member = member;
        this.project = project;
        this.day = day;
        this.tag = tag;
        this.issue = issue;
    }
}
