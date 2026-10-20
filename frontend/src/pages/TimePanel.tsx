import { useEffect, useState, type FormEvent } from 'react'
import { Clock3, LoaderCircle, Pause, Plus, Play, Square, Trash2 } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'
import { JiraIssuePicker, type JiraIssueLink } from '../components/JiraIssuePicker'
import { Modal } from '../components/Modal'
import { TimeEntryHistory } from './TimeEntryHistory'
import type { Project, Team, TimeEntry } from '../lib/api'
import { useProject, useProjects } from '../lib/projects'
import { useTeams } from '../lib/teams'
import { LONG_TIMER_SECONDS, MAX_MANUAL_DURATION_SECONDS, clearTimerLocalState, dismissLongTimer, formatDuration, isLongTimerDismissed, maxDurationMessage, readLastProject, readTimerDraft, saveLastProject, saveTimerDraft, teamToday, useActiveTimer, useChangeTimerState, useDiscardTimer, useManualTimeEntry, useStartTimer, useStopTimer, workDateRange, workDateRangeMessage } from '../lib/time'

const dateLabel = (date: string | null) => date === null ? 'Bez daty' : new Date(`${date}T12:00:00`).toLocaleDateString('pl-PL', { dateStyle: 'medium' })

function ManualTimeModal({ userId, team, projects, projectId, onClose, onSaved }: {
  userId: string; team: Team; projects: Project[]; projectId: string; onClose: () => void; onSaved: (entry: TimeEntry) => void
}) {
  const save = useManualTimeEntry(userId)
  const [selected, setSelected] = useState(projectId)
  const [date, setDate] = useState(() => teamToday(team.timezone))
  const [undated, setUndated] = useState(false)
  const range = workDateRange(team.timezone)
  const [hours, setHours] = useState('0')
  const [minutes, setMinutes] = useState('0')
  const [seconds, setSeconds] = useState('0')
  const [description, setDescription] = useState('')
  const [issue, setIssue] = useState<JiraIssueLink | null>(null)
  const [validation, setValidation] = useState('')
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const parts = [hours, minutes, seconds].map(value => value === '' ? 0 : Number(value))
    const duration = parts[0]! * 3600 + parts[1]! * 60 + parts[2]!
    if (!projects.some(project => project.id === selected)) { setValidation('Wybierz projekt.'); return }
    if (!undated && !date) { setValidation('Wybierz datę pracy.'); return }
    if (!undated && (date < range.min || date > range.max)) { setValidation(workDateRangeMessage); return }
    if (parts.some(value => !Number.isInteger(value) || value < 0) || parts[1]! > 59 || parts[2]! > 59 || duration < 1) {
      setValidation('Podaj dodatni czas w pełnych sekundach. Minuty i sekundy muszą mieścić się w zakresie 0–59.'); return
    }
    if (duration > MAX_MANUAL_DURATION_SECONDS) { setValidation(maxDurationMessage); return }
    setValidation('')
    save.mutate({ teamId: team.id, projectId: selected, workDate: undated ? null : date, durationSeconds: duration, description: description.trim() || null, jiraIssueId: issue?.id ?? null }, { onSuccess: onSaved })
  }
  return <Modal title="Dodaj wpis ręczny" busy={save.isPending} onClose={onClose}>
    <p className="dialog-description">Zespół: <strong>{team.name}</strong>. Dodaj czas pracy bez uruchamiania timera.</p>
    <form onSubmit={submit} onInput={() => setValidation('')}>
      <fieldset disabled={save.isPending}>
        <div className="field-group"><label htmlFor="manual-project">Projekt</label><select className="text-input" id="manual-project" value={selected} required onChange={event => setSelected(event.target.value)}><option value="" disabled>Wybierz projekt</option>{projects.map(project => <option key={project.id} value={project.id}>{project.name}{project.archivedAt ? ' (archiwalny)' : ''}</option>)}</select></div>
        <label><input type="checkbox" checked={undated} onChange={event => setUndated(event.target.checked)} /> Bez daty</label>
        <p className="field-hint">Wpis bez daty nie jest wliczany do podsumowań dnia ani tygodnia. Datę możesz uzupełnić później.</p>
        <div className="field-group"><label htmlFor="manual-date">Data pracy</label><input className="text-input" id="manual-date" type="date" required={!undated} disabled={undated} min={range.min} max={range.max} value={undated ? '' : date} onChange={event => setDate(event.target.value)} /></div>
        <div className="field-group"><p className="duration-label" id="duration-label">Czas trwania</p><div className="duration-fields" role="group" aria-labelledby="duration-label">
          <div><label htmlFor="manual-hours">Godziny</label><input className="text-input" id="manual-hours" type="number" min={0} max={24} step={1} value={hours} onChange={event => setHours(event.target.value)} /></div>
          <div><label htmlFor="manual-minutes">Minuty</label><input className="text-input" id="manual-minutes" type="number" min={0} max={59} step={1} value={minutes} onChange={event => setMinutes(event.target.value)} /></div>
          <div><label htmlFor="manual-seconds">Sekundy</label><input className="text-input" id="manual-seconds" type="number" min={0} max={59} step={1} value={seconds} onChange={event => setSeconds(event.target.value)} /></div>
        </div></div>
        <div className="field-group"><label htmlFor="manual-description">Opis pracy <span className="optional-label">(opcjonalnie)</span></label><textarea className="text-input project-description-input" id="manual-description" rows={3} maxLength={2000} value={description} onChange={event => setDescription(event.target.value)} /></div>
        <JiraIssuePicker userId={userId} teamId={team.id} value={issue} onChange={setIssue} />
      </fieldset>
      {(validation || save.isError) && <p className="form-error" role="alert">{validation || save.error?.message}</p>}
      <footer className="dialog-actions"><button type="button" className="button-secondary" disabled={save.isPending} onClick={onClose}>Anuluj</button><button type="submit" className="button-primary" disabled={save.isPending}>{save.isPending && <LoaderCircle size={16} className="spin" />}{save.isPending ? 'Zapisywanie…' : 'Zapisz wpis'}</button></footer>
    </form>
  </Modal>
}

function DiscardTimerModal({ userId, entry, discard, onClose, onDiscarded }: {
  userId: string; entry: TimeEntry; discard: ReturnType<typeof useDiscardTimer>; onClose: () => void; onDiscarded: () => void
}) {
  return <Modal title="Odrzuć timer" busy={discard.isPending} onClose={onClose}>
    <p className="dialog-description">Odrzucić uruchomiony timer? Czas pracy z tego timera nie zostanie zapisany, a tej operacji nie można cofnąć.</p>
    {discard.isError && <p className="form-error" role="alert">{discard.error.message}</p>}
    <footer className="dialog-actions"><button type="button" className="button-secondary" disabled={discard.isPending} onClick={onClose}>Anuluj</button>
      <button type="button" className="button-primary button-danger" disabled={discard.isPending} onClick={() => discard.mutate({ entryId: entry.id }, { onSuccess: () => { clearTimerLocalState(userId, entry.id); onDiscarded() } })}>{discard.isPending && <LoaderCircle size={16} className="spin" />}{discard.isPending ? 'Odrzucanie…' : 'Odrzuć timer'}</button></footer>
  </Modal>
}

function RunningTimer({ userId, entry, teams, stop, state, discard, onStopped, onDiscarded }: {
  userId: string; entry: TimeEntry; teams: Team[]; stop: ReturnType<typeof useStopTimer>; state: ReturnType<typeof useChangeTimerState>
  discard: ReturnType<typeof useDiscardTimer>; onStopped: (entry: TimeEntry) => void; onDiscarded: () => void
}) {
  const [now, setNow] = useState(Date.now)
  const [description, setDescriptionState] = useState(() => readTimerDraft(userId, entry.id) ?? entry.description ?? '')
  const [confirmDiscard, setConfirmDiscard] = useState(false)
  const [longDismissed, setLongDismissed] = useState(() => isLongTimerDismissed(userId, entry.id))
  const setDescription = (value: string) => { setDescriptionState(value); saveTimerDraft(userId, entry.id, value) }
  useEffect(() => {
    const interval = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(interval)
  }, [])
  const startedAt = Date.parse(entry.startedAt!)
  const paused = entry.pausedAt !== null
  const end = entry.pausedAt ? Date.parse(entry.pausedAt) : Math.max(now, Date.parse(entry.updatedAt))
  const elapsed = Math.max(0, Math.floor((end - startedAt) / 1000 - entry.pausedDurationSeconds))
  const busy = stop.isPending || state.isPending || discard.isPending
  const longRunning = elapsed >= LONG_TIMER_SECONDS && !longDismissed
  const team = teams.find(item => item.id === entry.teamId)
  const project = useProject(userId, entry.teamId, entry.projectId, !!team)
  return <section className={`running-timer overview-panel ${paused ? 'timer-paused' : ''}`} aria-label="Aktywny timer">
    <div className="timer-heading"><span className="timer-running-label"><span aria-hidden="true" />{paused ? 'Praca wstrzymana' : 'Timer działa'}</span><span className="field-hint">Od {new Date(entry.startedAt!).toLocaleTimeString('pl-PL', { hour: '2-digit', minute: '2-digit' })}</span></div>
    <p className="timer-project-name">{team && project.data ? project.data.name : project.isPending && team ? 'Wczytywanie projektu…' : 'Projekt niedostępny'}</p>
    <p className="timer-team-name">{team?.name ?? 'Poprzedni zespół'} · {dateLabel(entry.workDate)}</p>
    <p className="timer-counter" role="timer" aria-label="Czas aktywnego timera" aria-live="off">{formatDuration(elapsed)}</p>
    {longRunning && <div className="project-notice" role="status">
      <p>Ten timer działa już ponad {LONG_TIMER_SECONDS / 3600} godzin pracy. Być może o nim zapomniałeś. Możesz zakończyć pracę (wpis poprawisz później w historii), odrzucić timer albo kontynuować.</p>
      <button type="button" className="button-secondary" onClick={() => { dismissLongTimer(userId, entry.id); setLongDismissed(true) }}>Kontynuuj</button>
    </div>}
    <form onSubmit={event => { event.preventDefault(); stop.mutate({ entryId: entry.id, description: description.trim() }, { onSuccess: stopped => { clearTimerLocalState(userId, entry.id); onStopped(stopped) } }) }}>
      <div className="field-group"><label htmlFor="stop-description">Opis wykonanej pracy <span className="optional-label">(opcjonalnie)</span></label><textarea className="text-input timer-description" id="stop-description" rows={2} maxLength={2000} disabled={busy} value={description} onChange={event => setDescription(event.target.value)} /></div>
      <div className="timer-stop-row"><p className="field-hint">{!team ? 'Po utracie dostępu do zespołu możesz zakończyć timer, ale nie możesz go wznowić.' : paused ? 'Czas przerwy nie jest wliczany do pracy.' : 'Zamknięcie strony nie zatrzymuje timera.'}</p><div className="timer-controls"><button type="button" className="button-secondary" disabled={busy} onClick={() => { discard.reset(); setConfirmDiscard(true) }}><Trash2 size={16} />Odrzuć timer</button><button type="button" className="button-secondary" disabled={busy || (paused && !team) || (!paused && elapsed < 1)} onClick={() => { stop.reset(); state.mutate({ action: paused ? 'resume' : 'pause', entryId: entry.id, description: description.trim() }) }}>{state.isPending ? <LoaderCircle size={16} className="spin" /> : paused ? <Play size={16} /> : <Pause size={16} />}{state.isPending ? 'Zapisywanie…' : paused ? 'Wznów pracę' : 'Wstrzymaj timer'}</button><button type="submit" className="button-primary timer-stop" disabled={busy || elapsed < 1}>{stop.isPending ? <LoaderCircle size={16} className="spin" /> : <Square size={15} />}{stop.isPending ? 'Kończenie…' : 'Zakończ pracę'}</button></div></div>
    </form>
    {confirmDiscard && <DiscardTimerModal userId={userId} entry={entry} discard={discard} onClose={() => setConfirmDiscard(false)} onDiscarded={onDiscarded} />}
  </section>
}

function TeamTimeEntry({ userId, team, start, canStart, onSaved, onStarted }: {
  userId: string; team: Team; start: ReturnType<typeof useStartTimer>; canStart: boolean; onSaved: (entry: TimeEntry) => void; onStarted: () => void
}) {
  const projects = useProjects(userId, team.id, false)
  // The remembered project is only a preselection: it applies if still in the active list, and the server rechecks on start.
  const [projectId, setProjectId] = useState(() => readLastProject(userId, team.id) ?? '')
  const [description, setDescription] = useState('')
  const [issue, setIssue] = useState<JiraIssueLink | null>(null)
  const [manual, setManual] = useState(false)
  const manualProjects = useProjects(userId, team.id, true, manual)
  const [validation, setValidation] = useState('')
  const selected = projects.data?.find(project => project.id === projectId)
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selected) { setValidation('Wybierz projekt.'); return }
    setValidation('')
    start.mutate({ teamId: team.id, projectId: selected.id, description: description.trim() || null, jiraIssueId: issue?.id ?? null }, {
      onSuccess: () => { saveLastProject(userId, team.id, selected.id); setDescription(''); setIssue(null); onStarted() },
    })
  }
  return <section className="time-entry-section" aria-label="Rejestrowanie czasu w zespole">
    <header className="teams-toolbar"><div><h2>{team.name}</h2><p>Wybierz projekt i sposób rejestrowania pracy.</p></div></header>
    {projects.isPending ? <p className="teams-loading" role="status"><LoaderCircle size={18} className="spin" />Ładowanie projektów…</p>
      : projects.isError ? <div className="dashboard-error" role="alert">{projects.error.message}<button onClick={() => void projects.refetch()}>Spróbuj ponownie</button></div>
      : projects.data.length === 0 ? <div className="overview-panel empty-state"><Clock3 size={28} strokeWidth={1.4} /><h3>Brak aktywnych projektów.</h3><p>{team.role === 'OWNER' ? 'Utwórz projekt, aby zacząć rejestrować czas.' : 'Poproś właściciela zespołu o utworzenie projektu.'}</p><Link className="text-link dashboard-teams-link" to={`/projects?${new URLSearchParams({ team: team.id })}`}>Przejdź do projektów</Link></div>
      : <div className="overview-panel time-entry-form"><form onSubmit={submit}>
        <fieldset disabled={start.isPending}>
          <div className="field-group"><label htmlFor="time-project">Projekt</label><select className="text-input" id="time-project" required value={selected?.id ?? ''} onChange={event => { setProjectId(event.target.value); setValidation(''); start.reset() }}><option value="" disabled>Wybierz projekt</option>{projects.data.map(project => <option key={project.id} value={project.id}>{project.name}</option>)}</select></div>
          <div className="field-group"><label htmlFor="time-description">Opis pracy <span className="optional-label">(opcjonalnie)</span></label><textarea className="text-input timer-description" id="time-description" rows={3} maxLength={2000} value={description} onChange={event => setDescription(event.target.value)} /></div>
          <JiraIssuePicker userId={userId} teamId={team.id} value={issue} onChange={setIssue} />
        </fieldset>
        {validation && <p className="form-error" role="alert">{validation}</p>}
        <div className="time-entry-actions"><button type="submit" className="button-primary" disabled={!canStart || start.isPending || projects.isFetching}>{start.isPending ? <LoaderCircle size={16} className="spin" /> : <Play size={16} />}{start.isPending ? 'Uruchamianie…' : 'Uruchom timer'}</button></div>
        <p className="field-hint">Możesz mieć jeden aktywny timer. Wpis ręczny dodasz także podczas jego działania.</p>
      </form></div>}
    {!projects.isError && !projects.isPending && <button type="button" className="button-secondary" disabled={start.isPending} onClick={() => setManual(true)}><Plus size={16} />Dodaj wpis ręczny</button>}
    {manual && (manualProjects.isPending || manualProjects.isError) && <Modal title="Dodaj wpis ręczny" busy={false} onClose={() => setManual(false)}>{manualProjects.isError ? <div role="alert">{manualProjects.error.message}<button onClick={() => void manualProjects.refetch()}>Spróbuj ponownie</button></div> : <p role="status">Ładowanie projektów…</p>}</Modal>}
    {manual && manualProjects.data && !manualProjects.isError && <ManualTimeModal userId={userId} team={team} projects={manualProjects.data} projectId={selected?.id ?? ''} onClose={() => setManual(false)} onSaved={entry => { setManual(false); onSaved(entry) }} />}
  </section>
}

export function TimePanel({ userId }: { userId: string }) {
  const teams = useTeams(userId)
  const active = useActiveTimer(userId)
  const start = useStartTimer(userId)
  const stop = useStopTimer(userId)
  const state = useChangeTimerState(userId)
  const discard = useDiscardTimer(userId)
  const [saved, setSaved] = useState<TimeEntry | null>(null)
  const [search, setSearch] = useSearchParams()
  const selectedId = search.get('team')
  const selected = selectedId ? teams.data?.find(team => team.id === selectedId) : teams.data?.[0]
  useEffect(() => {
    if (!selectedId && selected) setSearch({ team: selected.id }, { replace: true })
  }, [selectedId, selected, setSearch])
  return <div className="time-panel">
    {saved && <p className="project-notice" role="status">{saved.entryType === 'MANUAL' ? 'Wpis ręczny zapisany' : 'Praca zakończona i zapisana'}: <strong>{formatDuration(saved.durationSeconds!)}</strong> · {dateLabel(saved.workDate)}.</p>}
    {active.isError && <div className="dashboard-error" role="alert">{active.error.message}<button onClick={() => void active.refetch()}>Odśwież timer</button></div>}
    {start.isError && <p className="form-error" role="alert">{start.error.message}</p>}
    {stop.isError && <p className="form-error" role="alert">{stop.error.message}</p>}
    {state.isError && <p className="form-error" role="alert">{state.error.message}</p>}
    {active.isPending ? <p className="teams-loading" role="status"><LoaderCircle size={18} className="spin" />Sprawdzanie aktywnego timera…</p>
      : active.data ? <RunningTimer key={active.data.id} userId={userId} entry={active.data} teams={teams.data ?? []} stop={stop} state={state} discard={discard} onStopped={entry => { setSaved(entry); start.reset(); state.reset() }} onDiscarded={() => { setSaved(null); start.reset(); state.reset(); stop.reset() }} />
      : active.isSuccess && <div className="timer-idle"><Clock3 size={18} /><span>Żaden timer nie jest uruchomiony.</span></div>}
    {teams.isPending ? <p className="teams-loading" role="status"><LoaderCircle size={18} className="spin" />Ładowanie zespołów…</p>
      : teams.isError ? <div className="dashboard-error" role="alert">{teams.error.message}<button onClick={() => void teams.refetch()}>Spróbuj ponownie</button></div>
      : teams.data.length === 0 ? <div className="overview-panel empty-state"><Clock3 size={28} strokeWidth={1.4} /><h3>Najpierw dołącz do zespołu.</h3><p>Czas pracy rejestrujesz w projekcie należącym do zespołu.</p><Link className="text-link dashboard-teams-link" to="/teams">Przejdź do zespołów</Link></div>
      : <><div className="project-team-picker"><div className="field-group"><label htmlFor="time-team">Zespół</label><select className="text-input" id="time-team" value={selected?.id ?? ''} disabled={start.isPending || stop.isPending || state.isPending} onChange={event => { setSearch({ team: event.target.value }); start.reset(); stop.reset(); state.reset() }}>{!selected && <option value="" disabled>Wybierz dostępny zespół</option>}{teams.data.map(team => <option key={team.id} value={team.id}>{team.name}</option>)}</select></div></div>
        {selected ? <>{selected.archivedAt ? <p className="project-notice" role="status">Zespół {selected.name} jest zarchiwizowany. Historia pozostaje dostępna, ale nie można dodawać ani zmieniać wpisów czasu.</p> : <TeamTimeEntry key={selected.id} userId={userId} team={selected} start={start} canStart={active.isSuccess && !active.isFetching && !active.data && !stop.isPending && !state.isPending} onSaved={setSaved} onStarted={() => { setSaved(null); stop.reset(); state.reset() }} />}
          <TimeEntryHistory key={`${userId}:${selected.id}`} userId={userId} teamId={selected.id} timezone={selected.timezone} readOnly={!!selected.archivedAt}
            canWorkAgain={active.isSuccess && !active.isFetching && !active.data && !start.isPending && !stop.isPending && !state.isPending && !discard.isPending}
            onWorkAgain={item => start.mutate({ teamId: selected.id, projectId: item.projectId, description: item.description }, {
              onSuccess: () => { saveLastProject(userId, selected.id, item.projectId); setSaved(null); stop.reset(); state.reset() },
            })} /></> : <p className="form-error" role="alert">Ten zespół nie istnieje lub nie masz do niego dostępu. Wybierz inny zespół.</p>}
      </>}
  </div>
}
