import { useMemo, useState } from 'react'
import { Clock3, Plus } from 'lucide-react'
import { Link, Navigate, useSearchParams } from 'react-router-dom'
import { useI18n } from '../../content/i18n'
import { formatDuration, formatLongDate } from '../../domain/duration'
import { useUser } from '../../app/session'
import { canManage } from '../../lib/api'
import { usePreferences } from '../../lib/preferences'
import { projectColorHex } from '../../lib/projectColors'
import { useTeams } from '../../lib/teams'
import { useActiveTimer } from '../../lib/time'
import { Button, Dot, LoadError, Loading, Notice } from '../../ui'
import { JiraKey } from '../entries/EntryExtras'
import { AddTimeCard } from './AddTime'
import { useWorkData } from './data'
import { MeasureCard } from './Measure'
import './work.css'

type Mode = 'timer' | 'manual'
type FormTarget = { teamId: string; projectId?: string }

export function MyWork() {
  const user = useUser()
  const { t, locale } = useI18n()
  const [search, setSearch] = useSearchParams()
  const weekStart = usePreferences(user.id).data?.weekStart ?? 'MONDAY'
  const teamsQuery = useTeams(user.id)
  const teams = useMemo(() => (teamsQuery.data ?? []).filter(team => !team.archivedAt), [teamsQuery.data])
  const data = useWorkData(user.id, teams, weekStart)
  const active = useActiveTimer(user.id)
  const [mode, setMode] = useState<Mode | null>(null)
  const [targets, setTargets] = useState<Partial<Record<Mode, FormTarget>>>({})
  const [savedLink, setSavedLink] = useState('')
  const scope = teams.find(team => team.id === search.get('team'))?.id ?? ''
  const perTeam = data.perTeam.filter(({ team }) => !scope || team.id === scope)
  const projectsFor = (teamId: string) => data.projects.filter(project => !teamId || project.teamId === teamId)
  const recentIds = (teamId: string) => [...new Set(data.recentProjects.filter(entry => !teamId || entry.teamId === teamId).map(entry => entry.projectId))]
  const today = perTeam.every(item => item.today !== undefined) ? perTeam.reduce((sum, item) => sum + (item.today ?? 0), 0) : undefined
  const entries = data.entries.filter(entry => !scope || entry.teamId === scope)
  const undatedTeams = perTeam.flatMap(({ team, undated }) => undated && (undated.count > 0 || scope === team.id) ? [{ team, undated }] : [])
  const history = historyLink({ view: 'list', team: scope })

  function open(next: Mode) {
    if (mode === next) {
      setMode(null)
      return
    }
    const available = projectsFor(scope)
    const projectId = recentIds(scope).find(id => available.some(project => project.id === id)) ?? available[0]?.id
    setTargets(current => current[next]?.projectId ? current : { ...current, [next]: { teamId: scope, projectId } })
    setMode(next)
  }

  if (teamsQuery.isError) return <div className="page"><LoadError onRetry={() => void teamsQuery.refetch()} retrying={teamsQuery.isFetching} message={teamsQuery.error.message} /></div>
  if (teamsQuery.isPending) return <div className="page"><Loading /></div>
  if (!teams.length) return <Navigate to="/app/start" replace />

  const scopeHasNoProjects = !data.projectsPending && !data.projectsError && !projectsFor(scope).length
  const canCreateProject = perTeam.some(({ team }) => canManage(team.role))
  const timer = targets.timer
  const manual = targets.manual
  return <div className="page work">
    <header className="page-head"><div><p className="eyebrow">{formatLongDate(new Date(), locale)}</p><h1>{t('work.title')}</h1></div></header>
    <div className="work-actions">
      <Button variant="primary" disabled={active.isPending || !!active.data || data.projectsPending} aria-expanded={mode === 'timer' && !active.data} onClick={() => open('timer')}><Clock3 size={18} aria-hidden="true" />{t('work.action')}</Button>
      <Button variant="primary" disabled={data.projectsPending} aria-expanded={mode === 'manual'} onClick={() => open('manual')}><Plus size={18} aria-hidden="true" />{t('work.add')}</Button>
    </div>
    {scopeHasNoProjects && <Notice tone="info" action={canCreateProject && <Link className="btn btn-sm" to={scope ? '/app/projects?team=' + encodeURIComponent(scope) : '/app/projects'}>{t('work.openProjects')}</Link>}>{t('work.noProjectsInScope')}</Notice>}
    {active.isError && <LoadError onRetry={() => void active.refetch()} />}
    {data.isError && <LoadError onRetry={() => void data.refetch()} retrying={data.isFetching} />}
    {(timer || active.data) && <div className="work-form" hidden={mode !== 'timer' && !active.data}>
      {!active.data && timer && !projectsFor(timer.teamId).length && <Notice tone="info">{t('work.noProjectsInScope')}</Notice>}
      <MeasureCard projects={projectsFor(timer?.teamId ?? scope)} allProjects={data.projects}
        recentProjectIds={timer?.projectId ? [timer.projectId, ...recentIds(timer.teamId).filter(id => id !== timer.projectId)] : []} loading={data.projectsPending} />
    </div>}
    {manual && <div className="work-form" hidden={mode !== 'manual'}>
      {!projectsFor(manual.teamId).length && <Notice tone="info">{t('work.noProjectsInScope')}</Notice>}
      <AddTimeCard projects={projectsFor(manual.teamId)} loading={data.projectsPending} defaultProjectId={manual.projectId}
        onSaved={(team, project, date, entry) => setSavedLink(historyLink({ team, project, entry, view: date ? 'list' : 'undated', date: date ?? '' }))} />
    </div>}
    {savedLink && <Link className="action-link work-result" to={savedLink}>{t('work.savedLink')}</Link>}

    <section className="card work-log" aria-labelledby="work-log-title">
      <div className="work-log-head">
        <h2 id="work-log-title">{t('work.today')} {today !== undefined && <span className="num">· {formatDuration(today)}</span>}</h2>
        <Link className="btn" to={history}>{t('work.history')}</Link>
      </div>
      {teams.length > 1 && <label className="work-scope">
        <span className="field-label">{t('work.filterScope')}</span>
        <select className="select input" value={scope} onChange={event => {
          const next = new URLSearchParams(search)
          if (event.target.value) next.set('team', event.target.value)
          else next.delete('team')
          setSearch(next, { replace: true })
        }}>
          <option value="">{t('work.scope.all')}</option>
          {teams.map(team => <option key={team.id} value={team.id}>{team.name}</option>)}
        </select>
      </label>}
      {data.entriesPending ? <Loading /> : entries.length ? <ul className="entries">{entries.map(entry => <li key={entry.id}>
        <Dot color={projectColorHex(entry.projectColor)} />
        <span className="entry-main"><b>{entry.projectName}</b>
          <small>{entry.team.name} · {t(entry.entryType === 'TIMER' ? 'work.kind.timer' : 'work.kind.manual')}</small>
          {entry.description && <span>{entry.description}</span>}
          <JiraKey userId={user.id} teamId={entry.teamId} issueKey={entry.jiraIssueKey} issueId={entry.jiraIssueId} />
        </span>
        <b className="num">{formatDuration(entry.durationSeconds)}</b>
      </li>)}</ul> : !data.isError && <p className="muted">{t('work.todayEmpty')}</p>}
      {undatedTeams.length > 0 &&
        <ul className="by-team">{undatedTeams.map(({ team, undated }) => <li key={team.id}>
          <Link className="text-link" to={historyLink({ team: team.id, view: 'undated' })}>{t('work.undatedLink', { count: undated.count })}{!scope && <> · {team.name}</>}</Link>
          <span className="num">{formatDuration(undated.seconds)}{!undated.complete && <small>{t('work.undatedPartial')}</small>}</span>
        </li>)}</ul>}
    </section>
  </div>
}

function historyLink(values: Record<string, string>) {
  return '/app/work/history?' + new URLSearchParams(Object.entries(values).filter(([, value]) => value))
}
