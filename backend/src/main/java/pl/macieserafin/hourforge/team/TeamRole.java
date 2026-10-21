package pl.macieserafin.hourforge.team;

public enum TeamRole {
    OWNER,
    ADMIN,
    MEMBER;

    /** Owners and admins run the team: members, invitations, projects, settings, reports and the Jira site. */
    public boolean canManage() {
        return this != MEMBER;
    }
}
