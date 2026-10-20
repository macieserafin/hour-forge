package pl.macieserafin.hourforge.support;

import java.time.Instant;
import pl.macieserafin.hourforge.project.Project;
import pl.macieserafin.hourforge.project.ProjectRepository;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamMember;
import pl.macieserafin.hourforge.team.TeamMemberRepository;
import pl.macieserafin.hourforge.team.TeamRepository;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.user.User;
import pl.macieserafin.hourforge.user.UserRepository;

public class TestData {
    private final UserRepository users;
    private final TeamRepository teams;
    private final ProjectRepository projects;
    private final TeamMemberRepository members;

    public TestData(UserRepository users, TeamRepository teams, ProjectRepository projects, TeamMemberRepository members) {
        this.users = users;
        this.teams = teams;
        this.projects = projects;
        this.members = members;
    }

    /** Users start with a confirmed address, as sign-in requires it; tests of the unconfirmed state clear it. */
    public User user(String email, String name) {
        var user = new User(email, name);
        user.setEmailVerifiedAt(Instant.now());
        return users.saveAndFlush(user);
    }
    public Team team(String name, String timezone) { return teams.saveAndFlush(new Team(name, timezone)); }
    public Project project(Team team, String name, String description) {
        return projects.saveAndFlush(new Project(team, name, description));
    }
    public TeamMember member(Team team, User user, TeamRole role, Instant joinedAt) {
        return members.saveAndFlush(new TeamMember(team, user, role, joinedAt));
    }
}
