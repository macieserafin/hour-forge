import { useState, type FormEvent } from 'react'
import { ChevronLeft, ChevronRight, History, LoaderCircle, RefreshCw } from 'lucide-react'
import type { TimeEntryHistoryFilters, TimeEntryHistoryItem } from '../lib/api'
import { DeleteTimeEntry, EditTimeEntry } from './TimeEntryActions'
import { useProjects } from '../lib/projects'
import { formatDuration, useTimeEntryHistory } from '../lib/time'

const emptyFilters: TimeEntryHistoryFilters = { projectId: '', from: '', to: '' }
const dateLabel = (date: string) => new Date(`${date}T12:00:00`).toLocaleDateString('pl-PL', { dateStyle: 'medium' })

export function TimeEntryHistory({ userId, teamId }: { userId: string; teamId: string }) {
  const projects = useProjects(userId, teamId, true)
  const [draft, setDraft] = useState(emptyFilters)
  const [filters, setFilters] = useState(emptyFilters)
  const [page, setPage] = useState(0)
  const [validation, setValidation] = useState('')
  const [action, setAction] = useState<{ type: 'edit' | 'delete'; entry: TimeEntryHistoryItem } | null>(null)
  const history = useTimeEntryHistory(userId, teamId, filters, page)
  const hasFilters = !!(filters.projectId || filters.from || filters.to)

  function apply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (draft.from && draft.to && draft.from > draft.to) {
      setValidation('Data „od” nie może być późniejsza niż data „do”.')
      return
    }
    setValidation('')
    setFilters({ ...draft })
    setPage(0)
  }

  function clear() {
    setDraft(emptyFilters)
    setFilters(emptyFilters)
    setPage(0)
    setValidation('')
  }

  return <section className="time-history" aria-labelledby="time-history-heading">
    <header className="teams-toolbar">
      <div><h2 id="time-history-heading">Historia czasu</h2><p>Twoje zapisane wpisy w wybranym zespole. Aktywny timer pojawi się tutaj po zakończeniu.</p></div>
      <button type="button" className="button-secondary" disabled={history.isFetching} onClick={() => void history.refetch()}>
        <RefreshCw size={15} className={history.isFetching ? 'spin' : ''} />Odśwież historię
      </button>
    </header>

    <form className="history-filters" aria-label="Filtry historii czasu" onSubmit={apply} onInput={() => setValidation('')}>
      <div className="field-group"><label htmlFor="history-project">Projekt w historii</label>
        <select className="text-input" id="history-project" value={draft.projectId} disabled={projects.isPending || projects.isError}
          onChange={event => setDraft({ ...draft, projectId: event.target.value })}>
          <option value="">Wszystkie projekty</option>
          {projects.data?.map(project => <option key={project.id} value={project.id}>{project.name}{project.archivedAt ? ' (archiwalny)' : ''}</option>)}
        </select>
      </div>
      <div className="field-group"><label htmlFor="history-from">Data od</label><input className="text-input" id="history-from" type="date" value={draft.from}
        onChange={event => setDraft({ ...draft, from: event.target.value })} /></div>
      <div className="field-group"><label htmlFor="history-to">Data do</label><input className="text-input" id="history-to" type="date" value={draft.to}
        onChange={event => setDraft({ ...draft, to: event.target.value })} /></div>
      <div className="history-filter-actions"><button type="submit" className="button-primary">Filtruj</button>
        <button type="button" className="button-secondary" onClick={clear}>Wyczyść filtry</button></div>
    </form>
    {validation && <p className="form-error" role="alert">{validation}</p>}
    {projects.isError && <div className="dashboard-error" role="alert">Nie udało się wczytać projektów do filtrowania.
      <button onClick={() => void projects.refetch()}>Wczytaj projekty ponownie</button></div>}

    {history.isPending ? <p className="teams-loading" role="status"><LoaderCircle size={18} className="spin" />Wczytywanie historii czasu…</p>
      : history.isError ? <div className="dashboard-error" role="alert">{history.error.message}
        <button onClick={() => void history.refetch()}>Spróbuj ponownie</button></div>
      : <>
        <p className="history-result-count" role="status">Liczba wpisów: {history.data.totalElements}{hasFilters ? ' · Po zastosowaniu filtrów' : ''}</p>
        {history.data.content.length === 0 ? <div className="overview-panel empty-state history-empty">
          <History size={28} strokeWidth={1.4} aria-hidden="true" />
          <h3>{page > 0 ? 'Na tej stronie nie ma już wpisów.' : hasFilters ? 'Brak wpisów dla wybranych filtrów.' : 'Nie masz jeszcze zapisanych wpisów.'}</h3>
          <p>{page > 0 ? 'Wróć na pierwszą stronę, aby zobaczyć aktualną historię.' : hasFilters ? 'Zmień projekt lub zakres dat albo wyczyść filtry.' : 'Zakończ timer lub dodaj wpis ręczny, aby zapisać czas pracy.'}</p>
          {page > 0 && <button className="button-secondary" onClick={() => setPage(0)}>Wróć na pierwszą stronę</button>}
        </div> : <div className="team-table-wrap history-table-wrap" tabIndex={0} role="region" aria-label="Wpisy historii czasu" aria-busy={history.isFetching}>
          <table className="team-table history-table">
            <caption className="sr-only">Twoje zakończone wpisy czasu, od najnowszej daty pracy</caption>
            <thead><tr><th scope="col">Data pracy</th><th scope="col">Projekt i opis pracy</th><th scope="col">Rodzaj</th><th scope="col">Czas</th><th scope="col">Akcje</th></tr></thead>
            <tbody>{history.data.content.map(entry => <tr key={entry.id}>
              <td><time dateTime={entry.workDate}>{dateLabel(entry.workDate)}</time></td>
              <td><strong className="history-project-name">{entry.projectName}</strong><p className="history-description">{entry.description || 'Bez opisu'}</p></td>
              <td><span className="history-entry-type">{entry.entryType === 'TIMER' ? 'Timer' : 'Wpis ręczny'}</span></td>
              <td className="history-duration">{formatDuration(entry.durationSeconds)}</td>
              <td><div className="history-actions"><button className="text-link" disabled={!projects.data || projects.isError} onClick={() => setAction({ type: 'edit', entry })}>Edytuj</button>
                <button className="text-link" onClick={() => setAction({ type: 'delete', entry })}>Usuń</button></div></td>
            </tr>)}</tbody>
          </table>
        </div>}
        {history.data.totalPages > 0 && <nav className="history-pagination" aria-label="Strony historii czasu">
          <p>Strona {history.data.page + 1} z {history.data.totalPages}</p>
          <div><button className="button-secondary" disabled={page === 0 || history.isFetching} onClick={() => setPage(page - 1)}><ChevronLeft size={16} />Poprzednia</button>
            <button className="button-secondary" disabled={page + 1 >= history.data.totalPages || history.isFetching} onClick={() => setPage(page + 1)}>Następna<ChevronRight size={16} /></button></div>
        </nav>}
      </>}
    {action?.type === 'edit' && projects.data && <EditTimeEntry userId={userId} entry={action.entry} projects={projects.data} onClose={() => setAction(null)} />}
    {action?.type === 'delete' && <DeleteTimeEntry userId={userId} entry={action.entry} onClose={() => setAction(null)} onDeleted={() => {
      setAction(null)
      if (page > 0 && history.data?.content.length === 1) setPage(page - 1)
    }} />}
  </section>
}
