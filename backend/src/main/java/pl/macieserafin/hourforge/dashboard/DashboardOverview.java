package pl.macieserafin.hourforge.dashboard;

import java.util.List;
import java.util.UUID;

/** Time of one person across all their teams; each team counts its own day and week in its own time zone. */
public record DashboardOverview(long todaySeconds, long weekSeconds, List<TeamTotal> teams) {
    public record TeamTotal(UUID teamId, String teamName, boolean archived, long todaySeconds, long weekSeconds) {
    }
}
