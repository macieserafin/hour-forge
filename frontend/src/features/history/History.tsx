import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { ArrowLeft, ChevronLeft, ChevronRight, Copy, Pencil, Play, Plus, Repeat, Trash2 } from 'lucide-react'
import { useQueries } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router-dom'
import { useI18n, useMessages } from '../../content/i18n'
import { formatDuration, formatWorkDate } from '../../domain/duration'
import { useUser } from '../../app/session'
import { getTimeEntryHistory, type Team, type TimeEntryHistoryItem } from '../../lib/api'
import { usePreferences } from '../../lib/preferences'
import { projectColorHex } from '../../lib/projectColors'
import { useTeams } from '../../lib/teams'
import { addDays, historyKey, teamToday, useStartTimer, useTimeEntryHistory } from '../../lib/time'
import { Button, Dialog, Dot, Empty, Field, LoadError, Loading, Notice } from '../../ui'
import { useToast } from '../../ui/toast'
import { JiraKey } from '../entries/EntryExtras'
import { useAllProjects, type WorkProject } from '../work/data'
import { DeleteEntryDialog, EntryDialog, RepeatDayDialog } from './EntryDialogs'
import './history.css'

const messages = {
  pl: {
    title: 'Historia pracy',
    week: 'Tydzień',
    list: 'Lista',
    undated: 'Bez daty',
    prevWeek: 'Poprzedni tydzień',
    nextWeek: 'Następny tydzień',
    thisWeek: 'Ten tydzień',
    project: 'Projekt',
    total: 'Suma',
    noWeek: 'W tym tygodniu nie ma zapisanej pracy w wybranym zakresie.',
    cell: '{project}, {day}: {duration}. Otwórz wpisy.',
    emptyCell: '{project}, {day}: brak czasu. Dodaj czas.',
    team: 'Zespół',
    teamNote: 'Wybierz zespół, aby zobaczyć jego wpisy.',
    search: 'Szukaj w opisach',
    from: 'Od',
    to: 'Do',
    allProjects: 'Wszystkie projekty',
    filter: 'Pokaż',
    clear: 'Wyczyść',
    badRange: 'Data „od” nie może być późniejsza niż „do”.',
    count: 'Wpisów: {count}',
    noEntries: 'Brak wpisów dla wybranych filtrów.',
    noUndated: 'Nie masz wpisów bez daty w tym zespole.',
    undatedNote: 'Praca bez daty ma osobną sumę i nie wlicza się do dni ani tygodni. Możesz nadać jej datę w edycji.',
    undatedSum: 'Czas na tej stronie: {duration}',
    prev: 'Poprzednia',
    next: 'Następna',
    page: 'Strona {page} z {pages}',
    timer: 'timer',
    manual: 'ręcznie',
    edited: 'poprawił(a): {name}',
    edit: 'Edytuj',
    delete: 'Usuń',
    again: 'Pracuj ponownie',
    addAgain: 'Dodaj ponownie',
    againLabel: 'Uruchom timer: {project}',
    addAgainLabel: 'Dodaj ponownie czas: {project}',
    started: 'Timer uruchomiony: {project}.',
    repeat: 'Powtórz wczorajszy dzień',
    cellTitle: '{project} · {day}',
    addHere: 'Dodaj czas',
    noCellEntries: 'Brak wpisów tego dnia w tym projekcie.',
  },
  en: {
    title: 'Work history',
    week: 'Week',
    list: 'List',
    undated: 'No date',
    prevWeek: 'Previous week',
    nextWeek: 'Next week',
    thisWeek: 'This week',
    project: 'Project',
    total: 'Total',
    noWeek: 'No work recorded this week in the selected scope.',
    cell: '{project}, {day}: {duration}. Open entries.',
    emptyCell: '{project}, {day}: no time. Add time.',
    team: 'Team',
    teamNote: 'Choose a team to see its entries.',
    search: 'Search descriptions',
    from: 'From',
    to: 'To',
    allProjects: 'All projects',
    filter: 'Show',
    clear: 'Clear',
    badRange: '"From" cannot be later than "to".',
    count: 'Entries: {count}',
    noEntries: 'No entries for the selected filters.',
    noUndated: 'You have no undated entries in this team.',
    undatedNote:
      'Work without a date has its own total and is not counted in days or weeks. You can give it a date when editing.',
    undatedSum: 'Time on this page: {duration}',
    prev: 'Previous',
    next: 'Next',
    page: 'Page {page} of {pages}',
    timer: 'timer',
    manual: 'manual',
    edited: 'edited by {name}',
    edit: 'Edit',
    delete: 'Delete',
    again: 'Work again',
    addAgain: 'Add again',
    againLabel: 'Start the timer: {project}',
    addAgainLabel: 'Add time again: {project}',
    started: 'Timer started: {project}.',
    repeat: 'Repeat yesterday',
    cellTitle: '{project} · {day}',
    addHere: 'Add time',
    noCellEntries: 'No entries in this project on this day.',
  },
}

type View = 'week' | 'list' | 'undated'

/** The week grid across teams, the filtered list and undated work, with edit, delete and copy. */
export function History() {
  const user = useUser()
  const { t } = useI18n()
  const m = useMessages(messages)
  const [search, setSearch] = useSearchParams()
  const teamsQuery = useTeams(user.id)
  const teams = useMemo(() => (teamsQuery.data ?? []).filter(team => !team.archivedAt), [teamsQuery.data])
  const all = useAllProjects(user.id, teams)
  const view: View =
    search.get('undated') === 'true'
      ? 'undated'
      : search.get('view') === 'list'
        ? 'list'
        : search.get('view') === 'undated'
          ? 'undated'
          : search.get('view') === 'week'
            ? 'week'
            : window.matchMedia('(max-width: 720px)').matches
              ? 'list'
              : 'week'
  const scope = teams.find(team => team.id === search.get('team')) ?? null
  const update = (changes: Record<string, string | null>) =>
    setSearch(
      previous => {
        const next = new URLSearchParams(previous)
        next.delete('undated')
        for (const [key, value] of Object.entries(changes))
          if (value === null) next.delete(key)
          else next.set(key, value)
        for (const key of ['date', 'project', 'entry']) next.delete(key)
        return next
      },
      { replace: true },
    )

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

  return (
    <div className="page history">
      <header className="page-head">
        <div>
          <Link className="back-link" to="/app/work">
            <ArrowLeft size={16} aria-hidden="true" />
            {t('nav.work')}
          </Link>
          <h1>{m('title')}</h1>
        </div>
        <div className="segmented" role="group" aria-label={m('title')}>
          {(['week', 'list', 'undated'] as const).map(item => (
            <button key={item} type="button" aria-pressed={view === item} onClick={() => update({ view: item })}>
              {m(item)}
            </button>
          ))}
        </div>
      </header>
      {view === 'week' ? (
        <WeekView
          teams={teams}
          scope={scope}
          projects={all.projects}
          onScope={team => update({ team })}
          weekParam={search.get('week')}
          onWeek={week => update({ week })}
        />
      ) : (
        <ListView
          key={search.toString()}
          undated={view === 'undated'}
          teams={teams}
          team={scope ?? teams[0] ?? null}
          projects={all.projects}
          savedDate={search.get('date') ?? ''}
          savedProject={search.get('project') ?? ''}
          savedEntry={search.get('entry') ?? ''}
          onTeam={team => update({ team, view })}
        />
      )}
    </div>
  )
}

function ScopeSwitch({
  teams,
  scope,
  onScope,
  allowAll,
}: {
  teams: Team[]
  scope: Team | null
  onScope: (teamId: string | null) => void
  allowAll: boolean
}) {
  const { t } = useI18n()
  if (teams.length < 2) return null
  return (
    <label className="work-scope">
      <span className="field-label">{t('work.scope')}</span>
      <select className="select input" value={scope?.id ?? ''} onChange={event => onScope(event.target.value || null)}>
        {allowAll && <option value="">{t('work.scope.all')}</option>}
        {teams.map(team => (
          <option key={team.id} value={team.id}>
            {team.name}
          </option>
        ))}
      </select>
    </label>
  )
}

/** Monday (or Sunday) of the week containing the date. */
function weekStartOf(isoDate: string, start: 'MONDAY' | 'SUNDAY') {
  const day = new Date(`${isoDate}T12:00:00Z`).getUTCDay()
  const back = start === 'MONDAY' ? (day + 6) % 7 : day
  return addDays(isoDate, -back)
}

const MAX_PAGES = 25

function WeekView({
  teams,
  scope,
  projects,
  onScope,
  weekParam,
  onWeek,
}: {
  teams: Team[]
  scope: Team | null
  projects: WorkProject[]
  onScope: (teamId: string | null) => void
  weekParam: string | null
  onWeek: (week: string | null) => void
}) {
  const user = useUser()
  const { locale } = useI18n()
  const m = useMessages(messages)
  const preferences = usePreferences(user.id)
  const weekStart = preferences.data?.weekStart ?? 'MONDAY'
  const today = teamToday(scope?.timezone ?? teams[0]?.timezone ?? 'Europe/Warsaw')
  const current = weekStartOf(today, weekStart)
  const from = weekParam && /^\d{4}-\d{2}-\d{2}$/.test(weekParam) ? weekStartOf(weekParam, weekStart) : current
  const to = addDays(from, 6)
  const days = Array.from({ length: 7 }, (_, index) => addDays(from, index))
  const inScope = scope ? [scope] : teams
  const [cell, setCell] = useState<{ project: WorkProject; day: string } | null>(null)

  // Transitional: one bounded request series per team for the week; the server will answer for all teams at once.
  const queries = useQueries({
    queries: inScope.map(team => ({
      queryKey: [...historyKey(user.id), 'week', team.id, from],
      queryFn: async ({ signal }: { signal: AbortSignal }) => {
        const items: TimeEntryHistoryItem[] = []
        for (let page = 0; page < MAX_PAGES; page++) {
          const result = await getTimeEntryHistory(team.id, { projectId: '', from, to }, page, signal)
          items.push(...result.content)
          if (page + 1 >= result.totalPages) break
        }
        return items
      },
    })),
  })
  const entries = queries.flatMap(query => query.data ?? [])
  const rows = useMemo(() => {
    const map = new Map<string, { project: WorkProject; byDay: Map<string, number> }>()
    for (const entry of entries) {
      if (!entry.workDate) continue
      const project =
        projects.find(item => item.id === entry.projectId) ??
        ({
          id: entry.projectId,
          name: entry.projectName,
          color: entry.projectColor ?? null,
          teamId: entry.teamId,
          team: teams.find(item => item.id === entry.teamId)!,
          description: null,
          createdAt: '',
          archivedAt: 'archived',
        } as WorkProject)
      const row = map.get(entry.projectId) ?? { project, byDay: new Map() }
      row.byDay.set(entry.workDate, (row.byDay.get(entry.workDate) ?? 0) + entry.durationSeconds)
      map.set(entry.projectId, row)
    }
    return [...map.values()].sort(
      (a, b) => a.project.team.name.localeCompare(b.project.team.name) || a.project.name.localeCompare(b.project.name),
    )
  }, [entries, projects, teams])
  const dayTotal = (day: string) => rows.reduce((sum, row) => sum + (row.byDay.get(day) ?? 0), 0)
  const rowTotal = (row: (typeof rows)[number]) => [...row.byDay.values()].reduce((sum, value) => sum + value, 0)
  const total = days.reduce((sum, day) => sum + dayTotal(day), 0)
  const pending = queries.some(query => query.isPending)
  const failed = queries.find(query => query.isError)
  const label = `${formatWorkDate(from, locale)} – ${formatWorkDate(to, locale)}`
  const cellEntries = cell
    ? entries.filter(entry => entry.projectId === cell.project.id && entry.workDate === cell.day)
    : []

  return (
    <section className="stack" aria-label={m('week')}>
      <div className="toolbar">
        <div className="week-nav">
          <button
            type="button"
            className="icon-btn"
            aria-label={m('prevWeek')}
            onClick={() => onWeek(addDays(from, -7))}
          >
            <ChevronLeft size={18} />
          </button>
          <b className="week-label">{label}</b>
          <button
            type="button"
            className="icon-btn"
            aria-label={m('nextWeek')}
            disabled={from >= current}
            onClick={() => onWeek(addDays(from, 7))}
          >
            <ChevronRight size={18} />
          </button>
          {from !== current && (
            <Button small onClick={() => onWeek(null)}>
              {m('thisWeek')}
            </Button>
          )}
        </div>
        <ScopeSwitch teams={teams} scope={scope} onScope={onScope} allowAll />
      </div>
      {failed && (
        <LoadError
          onRetry={() => void Promise.all(queries.map(query => query.refetch()))}
          message={failed.error?.message}
        />
      )}
      {pending ? (
        <div className="skeleton" style={{ height: 240 }} />
      ) : (
        <>
          <div className="card grid-card">
            <table className="week-grid">
              <caption className="sr-only">
                {m('week')} {label}
              </caption>
              <thead>
                <tr>
                  <th scope="col">{m('project')}</th>
                  {days.map(day => (
                    <th scope="col" key={day} className={day === today ? 'is-today' : ''}>
                      {formatWorkDate(day, locale, true)}
                    </th>
                  ))}
                  <th scope="col">{m('total')}</th>
                </tr>
              </thead>
              <tbody>
                {rows.map(row => (
                  <tr key={row.project.id}>
                    <th scope="row">
                      <span className="cell-project">
                        <Dot color={projectColorHex(row.project.color)} />
                        <span>
                          <b>{row.project.name}</b>
                          <small>{row.project.team.name}</small>
                        </span>
                      </span>
                    </th>
                    {days.map(day => {
                      const seconds = row.byDay.get(day) ?? 0
                      const dayLabel = formatWorkDate(day, locale, true)
                      return (
                        <td key={day} className={day === today ? 'is-today' : ''}>
                          <button
                            type="button"
                            className={`cell ${seconds ? 'has-time' : ''}`}
                            disabled={day > today}
                            aria-label={
                              seconds
                                ? m('cell', {
                                    project: row.project.name,
                                    day: dayLabel,
                                    duration: formatDuration(seconds),
                                  })
                                : m('emptyCell', { project: row.project.name, day: dayLabel })
                            }
                            onClick={() => setCell({ project: row.project, day })}
                          >
                            {seconds ? formatDuration(seconds) : <Plus size={14} aria-hidden="true" />}
                          </button>
                        </td>
                      )
                    })}
                    <td className="num row-total">{formatDuration(rowTotal(row))}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <th scope="row">{m('total')}</th>
                  {days.map(day => (
                    <td key={day} className="num">
                      {dayTotal(day) ? formatDuration(dayTotal(day)) : '—'}
                    </td>
                  ))}
                  <td className="num row-total">{formatDuration(total)}</td>
                </tr>
              </tfoot>
            </table>
            {!rows.length && <p className="muted grid-empty">{m('noWeek')}</p>}
          </div>
          <ol className="week-days">
            {days.map(day => {
              const items = rows.filter(row => row.byDay.get(day))
              return (
                <li key={day} className={day === today ? 'is-today' : ''}>
                  <details open={day === today}>
                    <summary>
                      <b>{formatWorkDate(day, locale, true)}</b>
                      <span className="num">{dayTotal(day) ? formatDuration(dayTotal(day)) : '—'}</span>
                    </summary>
                    <ul>
                      {items.map(row => (
                        <li key={row.project.id}>
                          <button type="button" onClick={() => setCell({ project: row.project, day })}>
                            <Dot color={projectColorHex(row.project.color)} />
                            <span>
                              {row.project.name}
                              <small>{row.project.team.name}</small>
                            </span>
                            <b className="num">{formatDuration(row.byDay.get(day)!)}</b>
                          </button>
                        </li>
                      ))}
                    </ul>
                    {day <= today && (items[0]?.project ?? projects[0]) && (
                      <Button small onClick={() => setCell({ project: items[0]?.project ?? projects[0]!, day })}>
                        <Plus size={14} aria-hidden="true" />
                        {m('addHere')}
                      </Button>
                    )}
                  </details>
                </li>
              )
            })}
          </ol>
          <p className="muted">
            {m('undatedNote')}{' '}
            <Link className="text-link" to="?view=undated">
              {m('undated')}
            </Link>
          </p>
        </>
      )}
      {cell && (
        <CellDialog
          project={cell.project}
          day={cell.day}
          entries={cellEntries}
          projects={projects}
          onClose={() => setCell(null)}
        />
      )}
    </section>
  )
}

function CellDialog({
  project,
  day,
  entries,
  projects,
  onClose,
}: {
  project: WorkProject
  day: string
  entries: TimeEntryHistoryItem[]
  projects: WorkProject[]
  onClose: () => void
}) {
  const { locale } = useI18n()
  const m = useMessages(messages)
  const [adding, setAdding] = useState(!entries.length)
  const [editing, setEditing] = useState<TimeEntryHistoryItem | null>(null)
  const [deleting, setDeleting] = useState<TimeEntryHistoryItem | null>(null)
  if (adding) return <EntryDialog preset={{ project, workDate: day }} projects={projects} onClose={onClose} />
  if (editing) return <EntryDialog entry={editing} projects={projects} onClose={onClose} />
  if (deleting) return <DeleteEntryDialog entry={deleting} onClose={onClose} />
  return (
    <Dialog
      open
      title={m('cellTitle', { project: project.name, day: formatWorkDate(day, locale, true) })}
      onClose={onClose}
    >
      {!entries.length ? (
        <p className="muted">{m('noCellEntries')}</p>
      ) : (
        <ul className="entries">
          {entries.map(entry => (
            <li key={entry.id}>
              <span className="entry-main">
                <b>{entry.description || '—'}</b>
                <small>{m(entry.entryType === 'TIMER' ? 'timer' : 'manual')}</small>
              </span>
              <b className="num">{formatDuration(entry.durationSeconds)}</b>
              <button
                type="button"
                className="icon-btn"
                aria-label={`${m('edit')}: ${entry.description ?? project.name}`}
                onClick={() => setEditing(entry)}
              >
                <Pencil size={16} />
              </button>
              <button
                type="button"
                className="icon-btn"
                aria-label={`${m('delete')}: ${entry.description ?? project.name}`}
                onClick={() => setDeleting(entry)}
              >
                <Trash2 size={16} />
              </button>
            </li>
          ))}
        </ul>
      )}
      <div className="dialog-actions">
        <Button variant="accent" onClick={() => setAdding(true)}>
          <Plus size={16} aria-hidden="true" />
          {m('addHere')}
        </Button>
      </div>
    </Dialog>
  )
}

function ListView({
  undated,
  teams,
  team,
  projects,
  savedDate,
  savedProject,
  savedEntry,
  onTeam,
}: {
  undated: boolean
  teams: Team[]
  team: Team | null
  projects: WorkProject[]
  savedDate: string
  savedProject: string
  savedEntry: string
  onTeam: (teamId: string) => void
}) {
  const user = useUser()
  const { t, locale } = useI18n()
  const m = useMessages(messages)
  const toast = useToast()
  const start = useStartTimer(user.id)
  const empty = { projectId: '', from: '', to: '', q: '' }
  const initial = { projectId: savedProject, from: savedDate, to: savedDate, q: '' }
  const [draft, setDraft] = useState(initial)
  const [filters, setFilters] = useState(initial)
  const [page, setPage] = useState(0)
  const [error, setError] = useState('')
  const [dialog, setDialog] = useState<
    { kind: 'edit' | 'delete' | 'again'; entry: TimeEntryHistoryItem } | { kind: 'repeat' } | null
  >(null)
  const history = useTimeEntryHistory(user.id, team?.id ?? '', { ...filters, undated }, page)
  const teamProjects = projects.filter(project => project.teamId === team?.id)
  useEffect(() => {
    if (!savedEntry || !history.data?.content.some(entry => entry.id === savedEntry)) return
    const row = document.getElementById(`entry-${savedEntry}`)
    row?.focus()
    row?.scrollIntoView({ block: 'center' })
  }, [savedEntry, history.data])
  if (!team) return <Empty title={m('noEntries')} />
  const data = history.data
  const apply = (event: FormEvent) => {
    event.preventDefault()
    if (draft.from && draft.to && draft.from > draft.to) {
      setError(m('badRange'))
      return
    }
    setError('')
    setFilters(draft)
    setPage(0)
  }
  const groups = new Map<string, TimeEntryHistoryItem[]>()
  for (const entry of data?.content ?? [])
    groups.set(entry.workDate ?? '', [...(groups.get(entry.workDate ?? '') ?? []), entry])

  return (
    <section className="stack" aria-label={m(undated ? 'undated' : 'list')}>
      <div className="toolbar">
        <ScopeSwitch
          teams={teams}
          scope={team}
          onScope={id => {
            if (id) {
              onTeam(id)
              setPage(0)
            }
          }}
          allowAll={false}
        />
        {!undated && (
          <Button small onClick={() => setDialog({ kind: 'repeat' })}>
            <Repeat size={15} aria-hidden="true" />
            {m('repeat')}
          </Button>
        )}
      </div>
      {teams.length > 1 && <p className="field-hint">{m('teamNote')}</p>}
      {undated && <Notice tone="info">{m('undatedNote')}</Notice>}
      <form className="filters card" onSubmit={apply} aria-label={m('filter')}>
        <Field label={m('search')}>
          {props => (
            <input
              {...props}
              className="input"
              type="search"
              value={draft.q}
              maxLength={100}
              onChange={event => setDraft({ ...draft, q: event.target.value })}
            />
          )}
        </Field>
        <Field label={m('project')}>
          {props => (
            <select
              {...props}
              className="select input"
              value={draft.projectId}
              onChange={event => setDraft({ ...draft, projectId: event.target.value })}
            >
              <option value="">{m('allProjects')}</option>
              {teamProjects.map(project => (
                <option key={project.id} value={project.id}>
                  {project.name}
                </option>
              ))}
            </select>
          )}
        </Field>
        {!undated && (
          <>
            <Field label={m('from')}>
              {props => (
                <input
                  {...props}
                  className="input"
                  type="date"
                  value={draft.from}
                  onChange={event => setDraft({ ...draft, from: event.target.value })}
                />
              )}
            </Field>
            <Field label={m('to')}>
              {props => (
                <input
                  {...props}
                  className="input"
                  type="date"
                  value={draft.to}
                  onChange={event => setDraft({ ...draft, to: event.target.value })}
                />
              )}
            </Field>
          </>
        )}
        <div className="filters-actions">
          <Button type="submit" variant="primary">
            {m('filter')}
          </Button>
          <Button
            onClick={() => {
              setDraft(empty)
              setFilters(empty)
              setPage(0)
              setError('')
            }}
          >
            {m('clear')}
          </Button>
        </div>
        {error && (
          <p className="field-error" role="alert">
            {error}
          </p>
        )}
      </form>
      {history.isError ? (
        <LoadError onRetry={() => void history.refetch()} message={history.error.message} />
      ) : history.isPending ? (
        <Loading />
      ) : !data?.content.length ? (
        <Empty title={m(undated ? 'noUndated' : 'noEntries')} />
      ) : (
        <>
          <p className="muted" role="status">
            {m('count', { count: data.totalElements })}
            {undated &&
              ` · ${m('undatedSum', { duration: formatDuration(data.content.reduce((sum, entry) => sum + entry.durationSeconds, 0)) })}`}
          </p>
          {[...groups.entries()].map(([date, items]) => (
            <section
              key={date || 'undated'}
              className="card day-group"
              aria-label={date ? formatWorkDate(date, locale, true) : t('work.undated')}
            >
              <div className="card-head">
                <h2>{date ? formatWorkDate(date, locale, true) : t('work.undated')}</h2>
                <b className="num">{formatDuration(items.reduce((sum, entry) => sum + entry.durationSeconds, 0))}</b>
              </div>
              <ul className="entries">
                {items.map(entry => {
                  const project = projects.find(item => item.id === entry.projectId)
                  return (
                    <li
                      key={entry.id}
                      id={`entry-${entry.id}`}
                      tabIndex={entry.id === savedEntry ? -1 : undefined}
                      className={entry.id === savedEntry ? 'saved-entry' : undefined}
                    >
                      <Dot color={projectColorHex(entry.projectColor)} />
                      <span className="entry-main">
                        <b>{entry.description || t('common.noDescription')}</b>
                        <small>
                          {entry.projectName} · {m(entry.entryType === 'TIMER' ? 'timer' : 'manual')}
                          {entry.tagName ? ` · ${entry.tagName}` : ''}
                          {entry.editedByName ? ` · ${m('edited', { name: entry.editedByName })}` : ''}{' '}
                          <JiraKey
                            userId={user.id}
                            teamId={entry.teamId}
                            issueKey={entry.jiraIssueKey}
                            issueId={entry.jiraIssueId}
                          />
                        </small>
                      </span>
                      <b className="num">{formatDuration(entry.durationSeconds)}</b>
                      <span className="row-actions">
                        {project && (
                          <button
                            type="button"
                            className="icon-btn"
                            aria-label={m('againLabel', { project: entry.projectName })}
                            disabled={start.isPending}
                            onClick={() =>
                              start.mutate(
                                {
                                  teamId: entry.teamId,
                                  projectId: entry.projectId,
                                  description: entry.description,
                                  ...(entry.tagId ? { tagId: entry.tagId } : {}),
                                  ...(entry.jiraIssueId ? { jiraIssueId: entry.jiraIssueId } : {}),
                                },
                                { onSuccess: () => toast(m('started', { project: entry.projectName })) },
                              )
                            }
                          >
                            <Play size={16} />
                          </button>
                        )}
                        {project && (
                          <button
                            type="button"
                            className="icon-btn"
                            aria-label={m('addAgainLabel', { project: entry.projectName })}
                            onClick={() => setDialog({ kind: 'again', entry })}
                          >
                            <Copy size={16} />
                          </button>
                        )}
                        <button
                          type="button"
                          className="icon-btn"
                          aria-label={`${m('edit')}: ${entry.projectName} ${entry.description ?? ''}`}
                          onClick={() => setDialog({ kind: 'edit', entry })}
                        >
                          <Pencil size={16} />
                        </button>
                        <button
                          type="button"
                          className="icon-btn"
                          aria-label={`${m('delete')}: ${entry.projectName} ${entry.description ?? ''}`}
                          onClick={() => setDialog({ kind: 'delete', entry })}
                        >
                          <Trash2 size={16} />
                        </button>
                      </span>
                    </li>
                  )
                })}
              </ul>
            </section>
          ))}
          {start.isError && <Notice tone="error">{start.error.message}</Notice>}
          {data.totalPages > 1 && (
            <nav className="pager" aria-label={m('page', { page: page + 1, pages: data.totalPages })}>
              <Button small disabled={page === 0 || history.isFetching} onClick={() => setPage(page - 1)}>
                <ChevronLeft size={15} aria-hidden="true" />
                {m('prev')}
              </Button>
              <span>{m('page', { page: page + 1, pages: data.totalPages })}</span>
              <Button
                small
                disabled={page + 1 >= data.totalPages || history.isFetching}
                onClick={() => setPage(page + 1)}
              >
                {m('next')}
                <ChevronRight size={15} aria-hidden="true" />
              </Button>
            </nav>
          )}
        </>
      )}
      {dialog?.kind === 'edit' && (
        <EntryDialog entry={dialog.entry} projects={projects} onClose={() => setDialog(null)} />
      )}
      {dialog?.kind === 'delete' && (
        <DeleteEntryDialog
          entry={dialog.entry}
          onClose={() => {
            setDialog(null)
            if (data && data.content.length === 1 && page > 0) setPage(page - 1)
          }}
        />
      )}
      {dialog?.kind === 'again' && projects.find(item => item.id === dialog.entry.projectId) && (
        <EntryDialog
          projects={projects}
          preset={{
            project: projects.find(item => item.id === dialog.entry.projectId)!,
            workDate: teamToday(team.timezone),
            description: dialog.entry.description,
            seconds: dialog.entry.durationSeconds,
          }}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog?.kind === 'repeat' && (
        <RepeatDayDialog
          teamId={team.id}
          teamName={team.name}
          timezone={team.timezone}
          onClose={() => setDialog(null)}
        />
      )}
    </section>
  )
}
