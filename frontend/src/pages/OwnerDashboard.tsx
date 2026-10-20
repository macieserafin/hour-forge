import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react'
import { useSearchParams } from 'react-router-dom'
import { ChevronLeft, ChevronRight, LoaderCircle, RefreshCw } from 'lucide-react'
import { ApiError, type ReportBucket, type ReportFilters, type ReportPage, type ReportSort, type Team } from '../lib/api'
import { MAX_REPORT_DAYS, clearedSearch, filtersFromSearch, filtersToSearch, reportRangeDays, reportRanges, useReportBreakdown, useReportEntries, useReportMembers, useReportProjects, useReportSummary } from '../lib/reports'
import { formatDuration } from '../lib/time'

const dateLabel = (date: string | null) => date === null ? 'Bez daty' : new Date(`${date}T12:00:00`).toLocaleDateString('pl-PL', { dateStyle: 'medium' })

interface QueryState<T> {
  data: T | undefined
  error: Error | null
  isPending: boolean
  isError: boolean
  isFetching: boolean
  refetch: () => unknown
}

function ReportState<T>({ query, children }: { query: QueryState<T>; children: (data: T) => ReactNode }) {
  if (query.isPending) return <p className="teams-loading" role="status"><LoaderCircle size={18} className="spin" />Wczytywanie raportu…</p>
  if (query.isError) return <div className="dashboard-error" role="alert">{query.error?.message}
    <button onClick={() => void query.refetch()}>Spróbuj ponownie</button></div>
  return <div aria-busy={query.isFetching}>{children(query.data!)}</div>
}

function Pagination({ data, busy, label, onPage }: { data: ReportPage<unknown>; busy: boolean; label: string; onPage: (page: number) => void }) {
  return <nav className="history-pagination" aria-label={label}>
    <p>Strona {data.page + 1}</p><div>
      <button className="button-secondary" disabled={data.page === 0 || busy} onClick={() => onPage(data.page - 1)}><ChevronLeft size={16} />Poprzednia</button>
      <button className="button-secondary" disabled={!data.hasNext || busy} onClick={() => onPage(data.page + 1)}>Następna<ChevronRight size={16} /></button>
    </div>
  </nav>
}

function BreakdownTable({ data, group, busy, onPage }: { data: ReportPage<ReportBucket>; group: 'MEMBER' | 'PROJECT'; busy: boolean; onPage: (page: number) => void }) {
  const people = group === 'MEMBER'
  return <>
    {data.content.length === 0 ? <p className="report-empty">Brak zapisanego czasu dla wybranych filtrów.</p>
      : <div className="team-table-wrap" tabIndex={0} role="region" aria-label={people ? 'Tabela czasu osób' : 'Tabela czasu projektów'}>
        <table className="team-table report-breakdown-table">
          <caption className="sr-only">{people ? 'Czas według osób' : 'Czas według projektów'}</caption>
          <thead><tr><th scope="col">{people ? 'Osoba' : 'Projekt'}</th><th scope="col">Czas</th><th scope="col">Wpisy</th></tr></thead>
          <tbody>{data.content.map(bucket => <tr key={(people ? bucket.userId : bucket.projectId)!}>
            <td>{people ? bucket.displayName : bucket.projectName}{!people && bucket.projectArchivedAt && <span className="report-meta">Projekt archiwalny</span>}</td>
            <td className="history-duration">{formatDuration(bucket.durationSeconds)}</td><td>{bucket.entryCount}</td>
          </tr>)}</tbody>
        </table>
      </div>}
    <Pagination data={data} busy={busy} label={people ? 'Strony czasu osób' : 'Strony czasu projektów'} onPage={onPage} />
  </>
}

export function OwnerDashboard({ userId, team }: { userId: string; team: Team }) {
  const [search, setSearch] = useSearchParams()
  // The address is the source of truth, so a report can be bookmarked, shared and reached with the back button.
  const filters = useMemo(() => filtersFromSearch(search, team.timezone), [search, team.timezone])
  const [draft, setDraft] = useState(filters)
  const [validation, setValidation] = useState('')
  const [memberPage, setMemberPage] = useState(0)
  const [projectPage, setProjectPage] = useState(0)
  const [entryPage, setEntryPage] = useState(0)
  const [sort, setSort] = useState<ReportSort>('WORK_DATE:DESC')
  const summary = useReportSummary(userId, team.id, filters)
  const people = useReportBreakdown(userId, team.id, filters, 'MEMBER', memberPage)
  const projects = useReportBreakdown(userId, team.id, filters, 'PROJECT', projectPage)
  const entries = useReportEntries(userId, team.id, filters, sort, entryPage)
  const memberOptions = useReportMembers(userId, team.id)
  const projectOptions = useReportProjects(userId, team.id)
  const queries = [summary, people, projects, entries, memberOptions, projectOptions]
  const accessError = queries.map(query => query.error).find(error => error instanceof ApiError && [401, 403, 404].includes(error.status))
  const busy = queries.some(query => query.isFetching)
  // A failed background refresh must never leave previously authorized data visible.
  const members = accessError || memberOptions.isError ? [] : memberOptions.data?.pages.flatMap(page => page.data.content) ?? []
  const options = accessError || projectOptions.isError ? [] : projectOptions.data?.pages.flatMap(page => page.data.content) ?? []

  function resetPages() { setMemberPage(0); setProjectPage(0); setEntryPage(0) }
  const filtersKey = JSON.stringify(filters)
  useEffect(() => { setDraft(filters); resetPages() }, [filtersKey]) // eslint-disable-line react-hooks/exhaustive-deps
  function validate(next: ReportFilters) {
    if (!next.undated && (!next.from || !next.to || next.from > next.to)) {
      return 'Podaj obie daty. Data „od” nie może być późniejsza niż data „do”.'
    }
    if (!next.undated && reportRangeDays(next.from, next.to) > MAX_REPORT_DAYS) return `Raport może obejmować najwyżej ${MAX_REPORT_DAYS} dni.`
    if ((next.q ?? '').length > 200) return 'Szukany tekst może mieć najwyżej 200 znaków.'
    return ''
  }
  function submitFilters(next: ReportFilters) {
    const message = validate(next)
    setValidation(message)
    if (message) return
    resetPages()
    setSearch(filtersToSearch(next, search), { replace: true })
  }
  function applyRange(range: { from: string; to: string }) { submitFilters({ ...draft, undated: false, ...range }) }
  function apply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    submitFilters(draft)
  }
  function clear() {
    setValidation('')
    setSearch(clearedSearch(search), { replace: true })
  }
  const ranges = reportRanges(team.timezone)

  return <section className="owner-dashboard" aria-label="Raport zespołu">
    <header className="teams-toolbar">
      <div><h2>Czas zespołu</h2><p>Zapisana praca w zespole {team.name}.</p></div>
      <button className="button-secondary" disabled={busy} onClick={() => void Promise.all(queries.map(query => query.refetch()))}>
        <RefreshCw size={15} className={busy ? 'spin' : ''} />Odśwież raport
      </button>
    </header>
    <div className="report-ranges" role="group" aria-label="Szybkie zakresy dat">
      <button type="button" className="button-secondary" onClick={() => applyRange(ranges.today)}>Dzisiaj</button>
      <button type="button" className="button-secondary" onClick={() => applyRange(ranges.week)}>Ten tydzień</button>
      <button type="button" className="button-secondary" onClick={() => applyRange(ranges.lastWeek)}>Poprzedni tydzień</button>
      <button type="button" className="button-secondary" onClick={() => applyRange(ranges.month)}>Ten miesiąc</button>
    </div>
    <form className="report-filters" aria-label="Filtry raportu zespołu" onSubmit={apply} onInput={() => setValidation('')}>
      <label><input type="checkbox" checked={!!draft.undated} onChange={event => setDraft({ ...draft, undated: event.target.checked, from: event.target.checked ? '' : ranges.week.from, to: event.target.checked ? '' : ranges.week.to })} /> Bez daty</label>
      <div className="field-group"><label htmlFor="report-from">Data od</label><input className="text-input" id="report-from" type="date" required={!draft.undated} disabled={draft.undated} min="0001-01-01" max="9999-12-31" value={draft.from} onChange={event => setDraft({ ...draft, from: event.target.value })} /></div>
      <div className="field-group"><label htmlFor="report-to">Data do</label><input className="text-input" id="report-to" type="date" required={!draft.undated} disabled={draft.undated} min="0001-01-01" max="9999-12-31" value={draft.to} onChange={event => setDraft({ ...draft, to: event.target.value })} /></div>
      <div className="field-group"><label htmlFor="report-person">Osoba</label>
        <select className="text-input" id="report-person" value={draft.userId} disabled={memberOptions.isPending || memberOptions.isError || !!accessError} onChange={event => setDraft({ ...draft, userId: event.target.value })}>
          <option value="">Wszystkie osoby</option>{members.map(member => <option key={member.userId} value={member.userId}>{member.displayName}{member.leftAt ? ' (były członek)' : ''}</option>)}
        </select>
        {memberOptions.hasNextPage && !accessError && <button type="button" className="text-link report-load-more" disabled={memberOptions.isFetching} onClick={() => void memberOptions.fetchNextPage()}>Wczytaj kolejne osoby</button>}
      </div>
      <div className="field-group"><label htmlFor="report-project">Projekt w raporcie</label>
        <select className="text-input" id="report-project" value={draft.projectId} disabled={projectOptions.isPending || projectOptions.isError || !!accessError} onChange={event => setDraft({ ...draft, projectId: event.target.value })}>
          <option value="">Wszystkie projekty</option>{options.map(project => <option key={project.projectId} value={project.projectId}>{project.name}{project.archivedAt ? ' (archiwalny)' : ''}</option>)}
        </select>
        {projectOptions.hasNextPage && !accessError && <button type="button" className="text-link report-load-more" disabled={projectOptions.isFetching} onClick={() => void projectOptions.fetchNextPage()}>Wczytaj kolejne projekty</button>}
      </div>
      <div className="field-group"><label htmlFor="report-type">Rodzaj wpisu</label><select className="text-input" id="report-type" value={draft.entryType} onChange={event => setDraft({ ...draft, entryType: event.target.value as ReportFilters['entryType'] })}>
        <option value="">Wszystkie rodzaje</option><option value="TIMER">Timer</option><option value="MANUAL">Wpis ręczny</option>
      </select></div>
      <div className="field-group"><label htmlFor="report-search">Szukaj w opisach</label><input className="text-input" id="report-search" type="search" maxLength={200} value={draft.q ?? ''} onChange={event => setDraft({ ...draft, q: event.target.value })} /></div>
      <div className="history-filter-actions"><button className="button-primary" type="submit">Filtruj raport</button><button className="button-secondary" type="button" onClick={clear}>Wyczyść filtry</button></div>
    </form>
    {validation && <p className="form-error" role="alert">{validation}</p>}
    {accessError ? <div className="dashboard-error" role="alert">{accessError.message}</div> : <>
      {memberOptions.isError && <div className="dashboard-error" role="alert">Nie udało się wczytać osób do filtrowania.<button onClick={() => void memberOptions.refetch()}>Wczytaj osoby ponownie</button></div>}
      {projectOptions.isError && <div className="dashboard-error" role="alert">Nie udało się wczytać projektów do filtrowania.<button onClick={() => void projectOptions.refetch()}>Wczytaj projekty ponownie</button></div>}
      <ReportState query={summary}>{data => <>
        <div className="dashboard-stats">
          <section className="overview-panel dashboard-stat" aria-label="Łączny czas zespołu"><h3>Łączny czas</h3><p className="dashboard-total">{formatDuration(data.totals.durationSeconds)}</p></section>
          <section className="overview-panel dashboard-stat" aria-label="Liczba wpisów zespołu"><h3>Zapisane wpisy</h3><p className="dashboard-total">{data.totals.entryCount.toLocaleString('pl-PL')}</p></section>
        </div>
        <p className="field-hint dashboard-summary-note">{data.context.filter!.undated ? 'Wpisy bez daty · Nie są wliczane do podsumowań dnia ani tygodnia.' : `${dateLabel(data.context.filter!.from)} – ${dateLabel(data.context.filter!.to)} · ${data.context.timezone}. Według daty pracy, z uwzględnieniem danych historycznych. Czas w toku nie jest wliczany.`}</p>
      </>}</ReportState>
      <div className="report-breakdowns">
        <section className="report-section" aria-label="Czas według osób"><h3>Czas według osób</h3><ReportState query={people}>{data => <BreakdownTable data={data.data} group="MEMBER" busy={people.isFetching} onPage={setMemberPage} />}</ReportState></section>
        <section className="report-section" aria-label="Czas według projektów"><h3>Czas według projektów</h3><ReportState query={projects}>{data => <BreakdownTable data={data.data} group="PROJECT" busy={projects.isFetching} onPage={setProjectPage} />}</ReportState></section>
      </div>
      <section className="report-section report-history" aria-label="Wpisy zespołu">
        <div className="teams-toolbar"><div><h3>Wpisy zespołu</h3><p>Zakończone wpisy wszystkich osób, wraz z opisami pracy.</p></div>
          <div className="field-group"><label htmlFor="report-sort">Kolejność wpisów</label><select className="text-input" id="report-sort" value={sort} onChange={event => { setSort(event.target.value as ReportSort); setEntryPage(0) }}>
            <option value="WORK_DATE:DESC">Data pracy: od najnowszej</option><option value="WORK_DATE:ASC">Data pracy: od najstarszej</option>
            <option value="DURATION:DESC">Czas: od najdłuższego</option><option value="DURATION:ASC">Czas: od najkrótszego</option>
            <option value="CREATED_AT:DESC">Dodano: od najnowszego</option><option value="CREATED_AT:ASC">Dodano: od najstarszego</option>
          </select></div>
        </div>
        <ReportState query={entries}>{data => <>
          {data.data.content.length === 0 ? <p className="report-empty">{entryPage > 0 ? 'Na tej stronie nie ma już wpisów. Wróć na poprzednią stronę.' : 'Brak wpisów dla wybranych filtrów.'}</p>
            : <div className="team-table-wrap" tabIndex={0} role="region" aria-label="Historia wpisów zespołu">
              <table className="team-table report-entry-table"><caption className="sr-only">Zapisane wpisy członków zespołu</caption>
                <thead><tr><th scope="col">Data pracy</th><th scope="col">Osoba</th><th scope="col">Projekt i opis pracy</th><th scope="col">Rodzaj</th><th scope="col">Czas</th></tr></thead>
                <tbody>{data.data.content.map(entry => <tr key={entry.id}>
                  <td data-label="Data pracy">{entry.workDate ? <time dateTime={entry.workDate}>{dateLabel(entry.workDate)}</time> : 'Bez daty'}</td>
                  <td data-label="Osoba">{entry.displayName}</td>
                  <td data-label="Projekt i opis pracy"><strong className="history-project-name">{entry.projectName}</strong>{entry.projectArchivedAt && <span className="report-meta">Projekt archiwalny</span>}<p className="history-description">{entry.description || 'Bez opisu'}</p></td>
                  <td data-label="Rodzaj"><span className="history-entry-type">{entry.entryType === 'TIMER' ? 'Timer' : 'Wpis ręczny'}</span></td>
                  <td data-label="Czas" className="history-duration">{formatDuration(entry.durationSeconds)}</td>
                </tr>)}</tbody>
              </table>
            </div>}
          <Pagination data={data.data} busy={entries.isFetching} label="Strony wpisów zespołu" onPage={setEntryPage} />
        </>}</ReportState>
      </section>
    </>}
  </section>
}
