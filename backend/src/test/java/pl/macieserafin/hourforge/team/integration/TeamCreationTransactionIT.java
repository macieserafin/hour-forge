package pl.macieserafin.hourforge.team.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import pl.macieserafin.hourforge.support.IntegrationTestSupport;
import pl.macieserafin.hourforge.team.CreateTeamRequest;
import pl.macieserafin.hourforge.team.TeamMember;
import pl.macieserafin.hourforge.team.TeamMemberRepository;
import pl.macieserafin.hourforge.team.TeamRepository;
import pl.macieserafin.hourforge.team.TeamService;
import pl.macieserafin.hourforge.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

class TeamCreationTransactionIT extends IntegrationTestSupport {
    @Autowired TeamService service;
    @Autowired UserRepository users;
    @Autowired TeamRepository teams;
    @MockitoSpyBean TeamMemberRepository members;

    @Test
    void rollsBackPersistedTeamIfOwnerCannotBeSaved() {
        var creator = fixtures.user("rollback-team@example.com", "Rollback Test");
        long teamCount = teams.count();
        long memberCount = members.count();
        doThrow(new DataIntegrityViolationException("Simulated membership failure"))
                .when(members).save(any(TeamMember.class));
        try {
            assertThatThrownBy(() -> service.createTeam(creator.getId(), new CreateTeamRequest("Rollback", "UTC")))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(teams.count()).isEqualTo(teamCount);
            assertThat(members.count()).isEqualTo(memberCount);
        } finally {
            users.deleteById(creator.getId());
        }
    }
}
