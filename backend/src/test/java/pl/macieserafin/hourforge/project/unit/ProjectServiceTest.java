package pl.macieserafin.hourforge.project.unit;

import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.project.Project;
import pl.macieserafin.hourforge.project.ProjectRepository;
import pl.macieserafin.hourforge.project.ProjectRequest;
import pl.macieserafin.hourforge.project.ProjectService;
import pl.macieserafin.hourforge.team.Team;
import pl.macieserafin.hourforge.team.TeamMember;
import pl.macieserafin.hourforge.team.TeamMemberRepository;
import pl.macieserafin.hourforge.team.TeamRepository;
import pl.macieserafin.hourforge.team.TeamRole;
import pl.macieserafin.hourforge.timeentry.EntryType;
import pl.macieserafin.hourforge.timeentry.TimeEntryRepository;
import pl.macieserafin.hourforge.user.User;
import pl.macieserafin.hourforge.user.UserRepository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static pl.macieserafin.hourforge.support.UnitFixtures.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {
    @Mock ProjectRepository projects;
    @Mock TeamMemberRepository members;
    @Mock TeamRepository teams;
    @Mock UserRepository users;
    @Mock TimeEntryRepository entries;
    @InjectMocks ProjectService service;
    private final User actor = user();
    private final Team team = team();
    private final Project project = project(team);

    private void access(TeamRole role) {
        when(users.existsById(actor.getId())).thenReturn(true);
        when(teams.findLockedById(team.getId())).thenReturn(Optional.of(team));
        when(members.findByTeamIdAndUserId(team.getId(), actor.getId()))
                .thenReturn(Optional.of(new TeamMember(team, actor, role, NOW)));
    }
    @Test void memberCannotCreateAProject() {
        access(TeamRole.MEMBER);
        assertThatThrownBy(() -> service.create(actor.getId(), team.getId(), new ProjectRequest("Project", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(403));
        verifyNoInteractions(projects);
    }
    @Test void ownerCreatesAProjectInTheirTeam() {
        access(TeamRole.OWNER);
        when(projects.saveAndFlush(any())).thenAnswer(call -> identified(call.getArgument(0)));
        assertThat(service.create(actor.getId(), team.getId(), new ProjectRequest(" Work ", " ")).teamId()).isEqualTo(team.getId());
    }
    @Test void activeTimerPreventsArchiving() {
        access(TeamRole.OWNER);
        when(projects.findByIdAndTeamId(project.getId(), team.getId())).thenReturn(Optional.of(project));
        when(entries.existsByProjectIdAndEntryTypeAndEndedAtIsNull(project.getId(), EntryType.TIMER)).thenReturn(true);
        assertThatThrownBy(() -> service.archive(actor.getId(), team.getId(), project.getId())).isInstanceOf(ResponseStatusException.class);
        assertThat(project.getArchivedAt()).isNull();
        verify(projects, never()).saveAndFlush(any());
    }
    @Test void archivesOnlyOnceAndPreservesTheProject() {
        access(TeamRole.OWNER);
        when(projects.findByIdAndTeamId(project.getId(), team.getId())).thenReturn(Optional.of(project));
        service.archive(actor.getId(), team.getId(), project.getId());
        var archivedAt = project.getArchivedAt();
        service.archive(actor.getId(), team.getId(), project.getId());
        assertThat(project.getArchivedAt()).isEqualTo(archivedAt).isNotNull();
        verify(projects, times(1)).saveAndFlush(project);
    }
    @Test void refusesEditsOfArchivedProjects() {
        access(TeamRole.OWNER);
        project.setArchivedAt(NOW);
        when(projects.findByIdAndTeamId(project.getId(), team.getId())).thenReturn(Optional.of(project));
        assertThatThrownBy(() -> service.update(actor.getId(), team.getId(), project.getId(), new ProjectRequest("Other", null)))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(project.getName()).isEqualTo("Project");
    }
}
