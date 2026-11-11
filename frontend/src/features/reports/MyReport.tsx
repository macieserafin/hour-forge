import { useMemo, useState } from 'react'
import { BarChart3, Download } from 'lucide-react'
import { useQueries } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { useI18n, useMessages } from '../../content/i18n'
import { formatDuration, formatWorkDate } from '../../domain/duration'
import { useUser } from '../../app/session'
import { canManage, getTimeEntryHistory, type Team, type TimeEntryHistoryItem } from '../../lib/api'
import { useDashboardActivity } from '../../lib/dashboard'
import { usePreferences } from '../../lib/preferences'
import { projectColorHex } from '../../lib/projectColors'
import { reportRanges, useDownloadReport } from '../../lib/reports'
import { useTeams } from '../../lib/teams'
import { historyKey } from '../../lib/time'
import { Button, Dot, Empty, LoadError, Loading, Notice } from '../../ui'
import './reports.css'

const messages = {
  pl: {
    title: 'Mój raport',
    intro: 'Twoja praca ze wszystkich zespołów. Raporty zespołów, którymi zarządzasz, znajdziesz poniżej.',
    week: 'Ten tydzień',
    lastWeek: 'Poprzedni tydzień',
    month: 'Ten miesiąc',
    undated: 'Bez daty',
    byDay: 'Czas według dni',
    total: 'Łącznie',
    entries: 'Wpisów: {count}',
    byTeam: 'Według zespołów',
    byProject: 'Według projektów',
    none: 'Brak pracy w wybranym okresie.',
    pdf: 'PDF',
    pdfLabel: 'Pobierz mój raport PDF: {team}',
    teamReports: 'Raporty zespołów',
    teamReport: 'Raport zespołu',
    activity: 'Aktywność w ostatnich 12 tygodniach',
    less: 'Mniej',
    more: 'Więcej',
    busiest: 'Najwięcej pracy: {day} ({duration})',
    capped: 'Pokazujemy pierwsze {count} wpisów zespołu w tym okresie; suma może być niepełna. Zawęź okres.',
    noTeams: 'Nie należysz do żadnego zespołu.',
    dateRange: '{from} – {to}',
  },
  en: {
    title: 'My report',
    intro: 'Your work from all teams. Reports of the teams you manage are below.',
    week: 'This week',
    lastWeek: 'Last week',
    month: 'This month',
    undated: 'No date',
    byDay: 'Time by day',
    total: 'Total',
    entries: 'Entries: {count}',
    byTeam: 'By team',
    byProject: 'By project',
    none: 'No work in the selected period.',
    pdf: 'PDF',
    pdfLabel: 'Download my PDF report: {team}',
    teamReports: 'Team reports',
    teamReport: 'Team report',
    activity: 'Activity in the last 12 weeks',
    less: 'Less',
    more: 'More',
    busiest: 'Most work: {day} ({duration})',
    capped:
      'Showing the first {count} entries of the team in this period; the total may be incomplete. Narrow the period.',
    noTeams: 'You do not belong to any team.',
    dateRange: '{from} – {to}',
  },
}

type Period = 'week' | 'lastWeek' | 'month' | 'undated'
const MAX_PAGES = 25

/** Own work across teams for a period, with undated work kept apart and the own PDF of each team. */
export function MyReport() {
  const user = useUser()
  const { locale } = useI18n()
  const m = useMessages(messages)
  const teamsQuery = useTeams(user.id)
  const weekStart = usePreferences(user.id).data?.weekStart ?? 'MONDAY'
  const teams = useMemo(() => teamsQuery.data ?? [], [teamsQuery.data])
  const [period, setPeriod] = useState<Period>('week')
  const ranges = reportRanges(teams[0]?.timezone ?? 'Europe/Warsaw', new Date(), weekStart)
  const range = period === 'undated' ? null : ranges[period]
  // Transitional: own totals are added up from each team's history; the server will answer at once.
  const queries = useQueries({
    queries: teams.map(team => ({
      queryKey: [...historyKey(user.id), 'my-report', team.id, period, range?.from],
      queryFn: async ({ signal }: { signal: AbortSignal }) => {
        const items: TimeEntryHistoryItem[] = []
        let complete = true
        for (let page = 0; page < MAX_PAGES; page++) {
          const result = await getTimeEntryHistory(
            team.id,
            { projectId: '', from: range?.from ?? '', to: range?.to ?? '', undated: !range },
            page,
            signal,
          )
          items.push(...result.content)
          if (page + 1 >= result.totalPages) break
          if (page + 1 === MAX_PAGES) complete = false
        }
        return { items, complete }
      },
    })),
  })

  if (teamsQuery.isPending)
    return (
      <div className="page">
        <Loading />
      </div>
    )
  if (teamsQuery.isError)
    return (
      <div className="page">
        <LoadError onRetry={() => void teamsQuery.refetch()} message={teamsQuery.error.message} />
      </div>
    )
  if (!teams.length)
    return (
      <div className="page">
        <Empty
          title={m('noTeams')}
          action={
            <Link className="btn btn-primary" to="/app/start">
              OK
            </Link>
          }
        />
      </div>
    )

  const perTeam = teams.map((team, index) => ({
    team,
    items: queries[index]?.data?.items ?? [],
    complete: queries[index]?.data?.complete ?? true,
  }))
  const all = perTeam.flatMap(item => item.items.map(entry => ({ ...entry, team: item.team })))
  const total = all.reduce((sum, entry) => sum + entry.durationSeconds, 0)
  const byProject = new Map<string, { name: string; team: Team; color?: string | null; seconds: number }>()
  for (const entry of all) {
    const row = byProject.get(entry.projectId) ?? {
      name: entry.projectName,
      team: entry.team,
      color: entry.projectColor,
      seconds: 0,
    }
    row.seconds += entry.durationSeconds
    byProject.set(entry.projectId, row)
  }
  const byDay = new Map<string, number>()
  for (const entry of all)
    if (entry.workDate) byDay.set(entry.workDate, (byDay.get(entry.workDate) ?? 0) + entry.durationSeconds)
  const days = [...byDay].sort(([a], [b]) => a.localeCompare(b))
  const maxDay = Math.max(1, ...byDay.values())
  const projects = [...byProject.values()].sort((a, b) => b.seconds - a.seconds)
  const pending = queries.some(query => query.isPending)
  const failed = queries.find(query => query.isError)
  const managed = teams.filter(team => canManage(team.role))

  return (
    <div className="page report">
      <header className="page-head">
        <div>
          <h1>{m('title')}</h1>
          <p className="muted">{m('intro')}</p>
        </div>
      </header>
      <div className="toolbar">
        <div className="segmented" role="group" aria-label={m('title')}>
          {(['week', 'lastWeek', 'month', 'undated'] as const).map(item => (
            <button key={item} type="button" aria-pressed={period === item} onClick={() => setPeriod(item)}>
              {m(item)}
            </button>
          ))}
        </div>
        {range && (
          <span className="muted">
            {m('dateRange', { from: formatWorkDate(range.from, locale), to: formatWorkDate(range.to, locale) })}
          </span>
        )}
      </div>
      {failed && (
        <LoadError
          onRetry={() => void Promise.all(queries.map(query => query.refetch()))}
          message={failed.error?.message}
        />
      )}
      {perTeam
        .filter(item => !item.complete)
        .map(item => (
          <Notice key={item.team.id} tone="warning">
            {item.team.name}: {m('capped', { count: MAX_PAGES * 20 })}
          </Notice>
        ))}
      <section className="totals" aria-label={m('total')}>
        <div className="stat stat-primary">
          <span>{m('total')}</span>
          <b>{pending ? '—' : formatDuration(total)}</b>
          <small>{m('entries', { count: all.length })}</small>
        </div>
      </section>
      {!pending && days.length > 0 && (
        <section className="card stack" aria-labelledby="report-days-title">
          <h2 id="report-days-title">{m('byDay')}</h2>
          <ul className="buckets report-days">
            {days.map(([day, seconds]) => (
              <li key={day} className="report-day">
                <time dateTime={day}>{formatWorkDate(day, locale, true)}</time>
                <span className="bucket-bar" aria-hidden="true">
                  <span style={{ width: `${(seconds / maxDay) * 100}%` }} />
                </span>
                <b className="num">{formatDuration(seconds)}</b>
              </li>
            ))}
          </ul>
        </section>
      )}
      <div className="split">
        <section className="card stack" aria-labelledby="by-team">
          <h2 id="by-team">{m('byTeam')}</h2>
          {pending ? (
            <div className="skeleton" style={{ height: 120 }} />
          ) : (
            <ul className="buckets static">
              {perTeam.map(({ team, items }) => {
                const seconds = items.reduce((sum, entry) => sum + entry.durationSeconds, 0)
                return (
                  <li key={team.id}>
                    <div className="bucket-row">
                      <span className="bucket-name">{team.name}</span>
                      <span className="bucket-bar" aria-hidden="true">
                        <span style={{ width: `${total ? (seconds / total) * 100 : 0}%` }} />
                      </span>
                      <span className="bucket-value num">{formatDuration(seconds)}</span>
                      <MinePdf team={team} range={range} />
                    </div>
                  </li>
                )
              })}
            </ul>
          )}
        </section>
        <section className="card stack" aria-labelledby="by-project">
          <h2 id="by-project">{m('byProject')}</h2>
          {pending ? (
            <div className="skeleton" style={{ height: 120 }} />
          ) : !projects.length ? (
            <p className="muted">{m('none')}</p>
          ) : (
            <ul className="buckets static">
              {projects.map((project, index) => (
                <li key={index}>
                  <div className="bucket-row">
                    <span className="bucket-name">
                      <Dot color={projectColorHex(project.color)} />
                      {project.name}
                      <small className="muted"> · {project.team.name}</small>
                    </span>
                    <span className="bucket-bar" aria-hidden="true">
                      <span style={{ width: `${total ? (project.seconds / total) * 100 : 0}%` }} />
                    </span>
                    <span className="bucket-value num">{formatDuration(project.seconds)}</span>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
      <Activity />
      {!!managed.length && (
        <section className="card stack" aria-labelledby="team-reports">
          <h2 id="team-reports">{m('teamReports')}</h2>
          <div className="row-inline">
            {managed.map(team => (
              <Link key={team.id} className="btn" to={`/app/teams/${encodeURIComponent(team.id)}/reports`}>
                <BarChart3 size={16} aria-hidden="true" />
                {team.name}
              </Link>
            ))}
          </div>
        </section>
      )}
    </div>
  )
}

function MinePdf({ team, range }: { team: Team; range: { from: string; to: string } | null }) {
  const m = useMessages(messages)
  const download = useDownloadReport(
    team.id,
    { from: range?.from ?? '', to: range?.to ?? '', undated: !range, userId: '', projectId: '', entryType: '' },
    'mine-pdf',
  )
  return (
    <span>
      <Button
        small
        variant="ghost"
        aria-label={m('pdfLabel', { team: team.name })}
        pending={download.isPending}
        onClick={() => download.mutate()}
      >
        <Download size={14} aria-hidden="true" />
        {m('pdf')}
      </Button>
      {download.isError && (
        <span className="field-error" role="alert">
          {download.error.message}
        </span>
      )}
    </span>
  )
}

/** The 12-week activity map lives in the own report. */
function Activity() {
  const user = useUser()
  const { locale } = useI18n()
  const m = useMessages(messages)
  const weekStart = usePreferences(user.id).data?.weekStart ?? 'MONDAY'
  const activity = useDashboardActivity(user.id, weekStart)
  if (!activity.data) return null
  const days = activity.data.days
  const max = Math.max(1, activity.data.maxSeconds)
  const busiest = [...days].sort((a, b) => b.seconds - a.seconds)[0]
  const level = (seconds: number) => (seconds === 0 ? 0 : Math.min(4, Math.ceil((seconds / max) * 4)))
  return (
    <section className="card stack" aria-labelledby="activity-title">
      <h2 id="activity-title">{m('activity')}</h2>
      <div
        className="heatmap"
        role="img"
        aria-label={
          busiest?.seconds
            ? m('busiest', {
                day: formatWorkDate(busiest.workDate, locale, true),
                duration: formatDuration(busiest.seconds),
              })
            : m('none')
        }
      >
        {days.map(day => (
          <span
            key={day.workDate}
            className={`heat heat-${level(day.seconds)}`}
            title={`${formatWorkDate(day.workDate, locale, true)}: ${formatDuration(day.seconds)}`}
          />
        ))}
      </div>
      <p className="heat-legend muted">
        {m('less')} <span className="heat heat-0" />
        <span className="heat heat-1" />
        <span className="heat heat-2" />
        <span className="heat heat-3" />
        <span className="heat heat-4" /> {m('more')}
      </p>
      {busiest && busiest.seconds > 0 && (
        <p className="muted">
          {m('busiest', {
            day: formatWorkDate(busiest.workDate, locale, true),
            duration: formatDuration(busiest.seconds),
          })}
        </p>
      )}
    </section>
  )
}
