import { useQueries } from '@tanstack/react-query'
import {
  getDashboardSummary,
  getProjects,
  getTimeEntryHistory,
  type Preferences,
  type Project,
  type Team,
  type TimeEntryHistoryItem,
} from '../../lib/api'
import { dashboardKey } from '../../lib/dashboard'
import { projectsKey } from '../../lib/projects'
import { historyKey, teamToday } from '../../lib/time'

/** The API returns team data separately, so the work log combines the current team scopes. */
export interface WorkProject extends Project {
  team: Team
}
export interface WorkEntry extends TimeEntryHistoryItem {
  team: Team
}

const MAX_UNDATED_PAGES = 10
const noFilter = { projectId: '', from: '', to: '' }

/** Active projects of all given teams, each with its team (the same cache as the per-team project lists). */
export function useAllProjects(userId: string, teams: Team[], includeArchived = false) {
  const queries = useQueries({
    queries: teams.map(team => ({
      queryKey: [...projectsKey(userId, team.id), 'list', includeArchived],
      queryFn: ({ signal }: { signal: AbortSignal }) => getProjects(team.id, includeArchived, signal),
    })),
  })
  return {
    queries,
    isPending: queries.some(query => query.isPending),
    isError: queries.some(query => query.isError),
    projects: queries.flatMap((query, index) =>
      (query.data ?? []).map(project => ({ ...project, team: teams[index]! })),
    ) as WorkProject[],
  }
}

export function useWorkData(userId: string, teams: Team[], weekStart: Preferences['weekStart']) {
  const projects = useAllProjects(userId, teams)
  const summaries = useQueries({
    queries: teams.map(team => ({
      queryKey: [...dashboardKey(userId), team.id, weekStart],
      queryFn: ({ signal }: { signal: AbortSignal }) => getDashboardSummary(team.id, signal, weekStart),
      refetchInterval: 60_000,
    })),
  })
  const todayEntries = useQueries({
    queries: teams.map(team => {
      const date = teamToday(team.timezone)
      return {
        queryKey: [...historyKey(userId), 'work-today', team.id, date],
        queryFn: ({ signal }: { signal: AbortSignal }) =>
          getTimeEntryHistory(team.id, { ...noFilter, from: date, to: date }, 0, signal),
      }
    }),
  })
  const recent = useQueries({
    queries: teams.map(team => ({
      queryKey: [...historyKey(userId), 'work-recent', team.id],
      queryFn: ({ signal }: { signal: AbortSignal }) => getTimeEntryHistory(team.id, noFilter, 0, signal),
    })),
  })
  const undated = useQueries({
    queries: teams.map(team => ({
      queryKey: [...historyKey(userId), 'work-undated', team.id],
      queryFn: async ({ signal }: { signal: AbortSignal }) => {
        let seconds = 0,
          count = 0
        for (let page = 0; page < MAX_UNDATED_PAGES; page++) {
          const result = await getTimeEntryHistory(team.id, { ...noFilter, undated: true }, page, signal)
          seconds += result.content.reduce((total, entry) => total + entry.durationSeconds, 0)
          count = result.totalElements
          if (page + 1 >= result.totalPages) return { seconds, count, complete: true }
        }
        return { seconds, count, complete: false }
      },
    })),
  })

  const all = [...projects.queries, ...summaries, ...todayEntries, ...undated]
  const byTeam = (index: number) => teams[index]!
  const entries: WorkEntry[] = todayEntries
    .flatMap((query, index) => (query.data?.content ?? []).map(entry => ({ ...entry, team: byTeam(index) })))
    .sort(
      (a, b) => (b.workDate ?? '').localeCompare(a.workDate ?? '') || (b.endedAt ?? '').localeCompare(a.endedAt ?? ''),
    )

  return {
    isError: all.some(query => query.isError),
    isFetching: all.some(query => query.isFetching),
    refetch: () => Promise.all(all.map(query => query.refetch())),
    projects: projects.projects,
    projectsPending: projects.isPending,
    projectsError: projects.isError,
    entries,
    entriesPending: todayEntries.some(query => query.isPending),
    recentProjects: recent
      .flatMap(query => query.data?.content ?? [])
      .sort(
        (a, b) =>
          (b.workDate ?? '').localeCompare(a.workDate ?? '') || (b.endedAt ?? '').localeCompare(a.endedAt ?? ''),
      ),
    perTeam: teams.map((team, index) => ({
      team,
      today: summaries[index]?.data?.todaySeconds,
      undated: undated[index]?.data,
    })),
  }
}
