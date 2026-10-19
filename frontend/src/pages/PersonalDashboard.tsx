import { useEffect, useState } from 'react'
import { Clock3, LoaderCircle } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'
import type { Team, TimeEntry } from '../lib/api'
import { useDashboardSummary } from '../lib/dashboard'
import { useProject } from '../lib/projects'
import { useTeams } from '../lib/teams'
import { formatDuration, useActiveTimer } from '../lib/time'
import { OwnerDashboard } from './OwnerDashboard'

const dateLabel = (date: string) => new Date(`${date}T12:00:00`).toLocaleDateString('pl-PL', { dateStyle: 'medium' })
const timeLink = (teamId: string) => `/time?${new URLSearchParams({ team: teamId })}`

function TimerOverview({ userId, entry, teams }: { userId: string; entry: TimeEntry; teams: Team[] }) {
  const [now, setNow] = useState(Date.now)
  useEffect(() => { const interval = window.setInterval(() => setNow(Date.now()), 1000); return () => window.clearInterval(interval) }, [])
  const team = teams.find(team => team.id === entry.teamId)
  const project = useProject(userId, entry.teamId, entry.projectId, !!team)
  const end = entry.pausedAt ? Date.parse(entry.pausedAt) : Math.max(now, Date.parse(entry.updatedAt))
  const elapsed = Math.max(0, Math.floor((end - Date.parse(entry.startedAt!)) / 1000 - entry.pausedDurationSeconds))
  return <section className="overview-panel dashboard-timer" aria-label="Aktywny timer na pulpicie">
    <h2>{entry.pausedAt ? 'Timer wstrzymany' : 'Praca w toku'}</h2>
    <p className="timer-project-name">{team && project.data ? project.data.name : project.isPending && team ? 'Wczytywanie projektu…' : 'Projekt niedostępny'}</p>
    <p className="timer-team-name">{team?.name ?? 'Poprzedni zespół'}</p>
    <p className="timer-counter" role="timer" aria-label="Czas aktywnego timera" aria-live="off">{formatDuration(elapsed)}</p>
    {project.isError && <p className="form-error" role="alert">{project.error.message}</p>}
    <p className="field-hint">Czas w toku nie jest wliczony do zapisanych godzin. Przerwy nie są naliczane.</p>
    <Link className="button-secondary" to={timeLink(entry.teamId)}>Przejdź do timera</Link>
  </section>
}

function Summary({ userId, team }: { userId: string; team: Team }) {
  const summary = useDashboardSummary(userId, team.id)
  if (summary.isPending) return <p className="teams-loading" role="status"><LoaderCircle className="spin" size={18} />Wczytywanie podsumowania…</p>
  if (summary.isError) return <div className="dashboard-error" role="alert">{summary.error.message}<button onClick={() => void summary.refetch()}>Spróbuj ponownie</button></div>
  const data = summary.data
  return <>
    <div className="dashboard-stats">
      <section className="overview-panel dashboard-stat" aria-label="Czas dzisiaj"><h2>Dzisiaj</h2><p className="dashboard-total">{formatDuration(data.todaySeconds)}</p><p className="field-hint">{dateLabel(data.today)}</p></section>
      <section className="overview-panel dashboard-stat" aria-label="Czas w tym tygodniu"><h2>Ten tydzień</h2><p className="dashboard-total">{formatDuration(data.weekSeconds)}</p><p className="field-hint">{dateLabel(data.weekStart)} – {dateLabel(data.weekEnd)}</p></section>
    </div>
    <p className="field-hint dashboard-summary-note">Zapisany czas w zespole {team.name} · {data.timezone}. Tydzień od poniedziałku do niedzieli.</p>
    <Link className="text-link" to={timeLink(team.id)}>Przejdź do historii i rejestru czasu</Link>
  </>
}

export function PersonalDashboard({ userId }: { userId: string }) {
  const teams = useTeams(userId)
  const active = useActiveTimer(userId)
  const [search, setSearch] = useSearchParams()
  const selectedId = search.get('team')
  const selected = selectedId ? teams.data?.find(team => team.id === selectedId) : teams.data?.[0]
  const teamView = selected?.role === 'OWNER' && search.get('view') === 'team'
  return <div className="personal-dashboard">
    {teams.isPending ? <p className="teams-loading" role="status"><LoaderCircle size={18} className="spin" />Ładowanie zespołów…</p>
      : teams.isError ? <div className="dashboard-error" role="alert">{teams.error.message}<button onClick={() => void teams.refetch()}>Wczytaj zespoły ponownie</button></div>
      : teams.data.length === 0 ? <section className="overview-panel empty-state"><Clock3 size={28} strokeWidth={1.4} /><h2>Zacznij od swojego zespołu.</h2><p>Utwórz zespół lub dołącz do istniejącego, aby zobaczyć podsumowanie pracy.</p><Link className="text-link dashboard-teams-link" to="/teams">Przejdź do zespołów</Link></section>
      : <>
        <div className="project-team-picker"><div className="field-group"><label htmlFor="dashboard-team">Zespół na pulpicie</label><select id="dashboard-team" className="text-input" value={selected?.id ?? ''} onChange={event => setSearch({ team: event.target.value, ...(search.get('view') === 'team' ? { view: 'team' } : {}) })}>
          {!selected && <option value="" disabled>Wybierz dostępny zespół</option>}{teams.data.map(team => <option key={team.id} value={team.id}>{team.name}</option>)}
        </select></div></div>
        {selected?.role === 'OWNER' && <div className="dashboard-view-switch" role="group" aria-label="Widok pulpitu">
          <button className="button-secondary" aria-pressed={!teamView} onClick={() => setSearch({ team: selected.id })}>Mój czas</button>
          <button className="button-secondary" aria-pressed={teamView} onClick={() => setSearch({ team: selected.id, view: 'team' })}>Zespół</button>
        </div>}
        {selected ? teamView ? <OwnerDashboard key={`${userId}:${selected.id}`} userId={userId} team={selected} /> : <Summary key={selected.id} userId={userId} team={selected} /> : <p className="form-error" role="alert">Ten zespół nie istnieje lub nie masz do niego dostępu. Wybierz inny zespół.</p>}
      </>}
    {active.isPending ? <p className="teams-loading" role="status"><LoaderCircle size={18} className="spin" />Sprawdzanie aktywnego timera…</p>
      : active.isError ? <div className="dashboard-error" role="alert">{active.error.message}<button onClick={() => void active.refetch()}>Odśwież timer</button></div>
      : active.data ? <TimerOverview userId={userId} entry={active.data} teams={teams.data ?? []} />
      : <p className="timer-idle dashboard-idle"><Clock3 size={18} />Żaden timer nie jest uruchomiony.</p>}
  </div>
}
