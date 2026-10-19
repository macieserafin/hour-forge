package pl.macieserafin.hourforge.dashboard.unit;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import pl.macieserafin.hourforge.dashboard.*;
import pl.macieserafin.hourforge.team.*;
import pl.macieserafin.hourforge.user.UserRepository;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static pl.macieserafin.hourforge.support.UnitFixtures.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {
    @Mock UserRepository users;
    @Mock TeamMemberRepository members;
    @Mock DashboardRepository dashboard;

    @ParameterizedTest
    @CsvSource({
        "2026-01-04T23:30:00Z, Pacific/Kiritimati, 2026-01-05, 2026-01-05",
        "2026-01-04T23:30:00Z, America/Los_Angeles, 2026-01-04, 2025-12-29",
        "2026-03-29T00:30:00Z, Europe/Warsaw, 2026-03-29, 2026-03-23"
    })
    void usesTeamTimezoneAndMondayToSundayBoundaries(String instant, String zone, LocalDate today, LocalDate start) {
        var actor = user();
        var team = identified(new Team("Team", zone));
        when(users.existsById(actor.getId())).thenReturn(true);
        when(members.findByTeamIdAndUserId(team.getId(), actor.getId())).thenReturn(Optional.of(new TeamMember(team, actor, TeamRole.MEMBER, NOW)));
        when(dashboard.totals(actor.getId(), team.getId(), today, start, start.plusDays(6))).thenReturn(new DashboardTotals(60, 120));
        var service = new DashboardService(users, members, dashboard, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
        var result = service.summary(actor.getId(), team.getId());
        assertThat(result.today()).isEqualTo(today);
        assertThat(result.weekStart()).isEqualTo(start);
        assertThat(result.weekEnd()).isEqualTo(start.plusDays(6));
        assertThat(result.todaySeconds()).isEqualTo(60);
        assertThat(result.weekSeconds()).isEqualTo(120);
    }

    @Test
    void refusesDeletedUsersBeforeReadingTeamData() {
        var service = new DashboardService(users, members, dashboard, CLOCK);
        assertThatThrownBy(() -> service.summary(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode().value()).isEqualTo(401));
        verifyNoInteractions(members, dashboard);
    }
}
