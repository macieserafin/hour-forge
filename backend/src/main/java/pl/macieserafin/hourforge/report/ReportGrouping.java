package pl.macieserafin.hourforge.report;

public enum ReportGrouping {
    DAY(false, false, true, false), MEMBER(true, false, false, false), PROJECT(false, true, false, false),
    MEMBER_PROJECT(true, true, false, false), DAY_MEMBER(true, false, true, false), DAY_PROJECT(false, true, true, false),
    TAG(false, false, false, true);

    final boolean member;
    final boolean project;
    final boolean day;
    final boolean tag;

    ReportGrouping(boolean member, boolean project, boolean day, boolean tag) {
        this.member = member;
        this.project = project;
        this.day = day;
        this.tag = tag;
    }
}
