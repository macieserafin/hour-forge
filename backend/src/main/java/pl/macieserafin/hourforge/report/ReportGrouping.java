package pl.macieserafin.hourforge.report;

public enum ReportGrouping {
    DAY(false, false, true), MEMBER(true, false, false), PROJECT(false, true, false),
    MEMBER_PROJECT(true, true, false), DAY_MEMBER(true, false, true), DAY_PROJECT(false, true, true);

    final boolean member;
    final boolean project;
    final boolean day;

    ReportGrouping(boolean member, boolean project, boolean day) {
        this.member = member;
        this.project = project;
        this.day = day;
    }
}
