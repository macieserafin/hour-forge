import { JiraKeyLink } from '../components/JiraKeyLink'
import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react'
import { useSearchParams } from 'react-router-dom'
import { ChevronLeft, ChevronRight, Download, LoaderCircle, RefreshCw } from 'lucide-react'
import { ApiError, type ReportBucket, type ReportFilters, type ReportGroup, type ReportPage, type ReportSort, type Team } from '../lib/api'
import { MAX_REPORT_DAYS, clearedSearch, filtersFromSearch, filtersToSearch, reportRangeDays, reportRanges, useDownloadReport, useReportActive, useReportBreakdown, useReportEntries, useReportTimesheet, useReportMembers, useReportProjects, useReportSummary } from '../lib/reports'
import { formatClock, formatDuration, formatHours } from '../lib/time'
import { ProjectDot } from '../components/ProjectDot'
import { TeamMark } from '../components/Avatar'
import { projectColorHex } from '../lib/projectColors'
import { useTags } from '../lib/tags'
import { useJiraStatus } from '../lib/jira'
import { BarChart, type ChartItem } from '../components/BarChart'

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

const breakdowns: Record<ReportGroup, { title: string; table: string; pages: string; columns: string[] }> = {
  MEMBER: { title: 'Czas według osób', table: 'Tabela czasu osób', pages: 'Strony czasu osób', columns: ['Osoba'] },
  PROJECT: { title: 'Czas według projektów', table: 'Tabela czasu projektów', pages: 'Strony czasu projektów', columns: ['Projekt'] },
  DAY: { title: 'Czas według dni', table: 'Tabela czasu według dni', pages: 'Strony czasu według dni', columns: ['Dzień'] },
  MEMBER_PROJECT: { title: 'Czas według osób i projektów', table: 'Tabela czasu osób i projektów', pages: 'Strony czasu osób i projektów', columns: ['Osoba', 'Projekt'] },
  TAG: { title: 'Czas według tagów', table: 'Tabela czasu tagów', pages: 'Strony czasu tagów', columns: ['Tag'] },
  JIRA_ISSUE: { title: 'Czas według zadań Jira', table: 'Tabela czasu zadań Jira', pages: 'Strony czasu zadań Jira', columns: ['Zadanie Jira'] },
}

const MAX_CHART_PROJECTS = 8

/** A chart is only drawn for a complete result; a single page of a longer list would misrepresent the whole. */
const complete = (data: ReportPage<ReportBucket>) => data.page === 0 && !data.hasNext && data.content.length > 0

function dayChart(data: ReportPage<ReportBucket>): ChartItem[] {
  const manyDays = data.content.length > 7
  return data.content.map(bucket => ({ key: bucket.workDate ?? 'none',
    label: bucket.workDate ? (manyDays ? bucket.workDate.slice(8) : new Date(`${bucket.workDate}T12:00:00`).toLocaleDateString('pl-PL', { weekday: 'short' })) : 'Bez daty',
    value: bucket.durationSeconds, display: `${dateLabel(bucket.workDate)}: ${formatDuration(bucket.durationSeconds)}`, short: formatHours(bucket.durationSeconds) }))
}

function projectChart(data: ReportPage<ReportBucket>): ChartItem[] {
  const sorted = [...data.content].sort((a, b) => b.durationSeconds - a.durationSeconds)
  const shown = sorted.slice(0, MAX_CHART_PROJECTS)
  const rest = sorted.slice(MAX_CHART_PROJECTS).reduce((sum, bucket) => sum + bucket.durationSeconds, 0)
  const items: ChartItem[] = shown.map(bucket => ({ key: bucket.projectId!, label: bucket.projectName ?? '', color: projectColorHex(bucket.projectColor),
    value: bucket.durationSeconds, display: formatDuration(bucket.durationSeconds), short: formatHours(bucket.durationSeconds) }))
  if (rest > 0) items.push({ key: 'rest', label: 'Pozostałe projekty', value: rest, display: formatDuration(rest), short: formatHours(rest) })
  return items
}

const archivedNote = (bucket: ReportBucket) => bucket.projectArchivedAt && <span className="report-meta">Projekt archiwalny</span>

function bucketCells(group: ReportGroup, bucket: ReportBucket, person: (bucket: ReportBucket) => ReactNode): ReactNode[] {
  switch (group) {
    case 'MEMBER': return [person(bucket)]
    case 'PROJECT': return [<>{bucket.projectName}{archivedNote(bucket)}</>]
    case 'DAY': return [bucket.workDate ? <time dateTime={bucket.workDate}>{dateLabel(bucket.workDate)}</time> : 'Bez daty']
    case 'MEMBER_PROJECT': return [person(bucket), <>{bucket.projectName}{archivedNote(bucket)}</>]
    case 'TAG': return [bucket.tagName ?? 'Bez tagu']
    case 'JIRA_ISSUE': return [bucket.jiraIssueKey ?? 'Bez zadania Jira']
  }
}

function BreakdownTable({ data, group, busy, onPage, profiles }: { data: ReportPage<ReportBucket>; group: ReportGroup; busy: boolean; onPage: (page: number) => void; profiles?: Map<string, string> }) {
  const labels = breakdowns[group]
  const person = (bucket: ReportBucket) => <>{bucket.displayName}{bucket.userId && profiles?.get(bucket.userId) && <span className="report-meta">{profiles.get(bucket.userId)}</span>}</>
  return <>
    {data.content.length === 0 ? <p className="report-empty">Brak zapisanego czasu dla wybranych filtrów.</p>
      : <div className="team-table-wrap" tabIndex={0} role="region" aria-label={labels.table}>
        <table className="team-table report-breakdown-table">
          <caption className="sr-only">{labels.title}</caption>
          <thead><tr>{labels.columns.map(column => <th key={column} scope="col">{column}</th>)}<th scope="col">Czas</th><th scope="col">Wpisy</th></tr></thead>
          <tbody>{data.content.map(bucket => <tr key={`${bucket.workDate ?? 'none'}|${bucket.userId ?? ''}|${bucket.projectId ?? ''}|${bucket.tagId ?? ''}|${bucket.jiraIssueKey ?? ''}`}>
            {bucketCells(group, bucket, person).map((cell, index) => <td key={index}>{cell}</td>)}
            <td className="history-duration">{formatDuration(bucket.durationSeconds)}</td><td>{bucket.entryCount}</td>
          </tr>)}</tbody>
        </table>
      </div>}
    <Pagination data={data} busy={busy} label={labels.pages} onPage={onPage} />
  </>
}

const startedAt = (value: string | null) => value ? new Date(value).toLocaleTimeString('pl-PL', { hour: '2-digit', minute: '2-digit' }) : ''

/** Time by tag. Only teams that have tags see it, so the others get no extra request or empty table. */
function TagBreakdown({ userId, team, filters }: { userId: string; team: Team; filters: ReportFilters }) {
  const [page, setPage] = useState(0)
  const filtersKey = JSON.stringify(filters)
  useEffect(() => setPage(0), [filtersKey])
  const tags = useReportBreakdown(userId, team.id, filters, 'TAG', page)
  return <section className="report-section" aria-label="Czas według tagów"><h3>Czas według tagów</h3>
    <ReportState query={tags}>{data => <BreakdownTable data={data.data} group="TAG" busy={tags.isFetching} onPage={setPage} />}</ReportState></section>
}

function JiraBreakdown({ userId, team, filters }: { userId: string; team: Team; filters: ReportFilters }) {
  const [page, setPage] = useState(0)
  const filtersKey = JSON.stringify(filters)
  useEffect(() => setPage(0), [filtersKey])
  const issues = useReportBreakdown(userId, team.id, filters, 'JIRA_ISSUE', page)
  return <section className="report-section" aria-label="Czas według zadań Jira"><h3>Czas według zadań Jira</h3>
    <ReportState query={issues}>{data => <BreakdownTable data={data.data} group="JIRA_ISSUE" busy={issues.isFetching} onPage={setPage} />}</ReportState></section>
}

const weekdayLabel = (date: string) => new Date(`${date}T12:00:00`).toLocaleDateString('pl-PL', { weekday: 'short', day: 'numeric', month: 'numeric' })

/** One row per person, one column per day of the chosen range, with totals. */
function TimesheetPanel({ userId, team, filters }: { userId: string; team: Team; filters: ReportFilters }) {
  const tooLong = !filters.undated && !!filters.from && !!filters.to && reportRangeDays(filters.from, filters.to) > 31
  const sheet = useReportTimesheet(userId, team.id, filters, !filters.undated && !tooLong)
  return <section className="report-section" aria-label="Karta czasu"><h3>Karta czasu</h3>
    {filters.undated ? <p className="report-empty">Karta czasu wymaga zakresu dat. Odznacz „Bez daty”.</p>
      : tooLong ? <p className="report-empty">Karta czasu obejmuje najwyżej 31 dni. Zawęź zakres dat.</p>
      : <ReportState query={sheet}>{data => data.rows.length === 0 ? <p className="report-empty">Brak zapisanego czasu w tym zakresie.</p> : <>
        <div className="team-table-wrap" tabIndex={0} role="region" aria-label="Tabela karty czasu">
          <table className="team-table report-timesheet-table"><caption className="sr-only">Czas pracy osób w kolejnych dniach</caption>
            <thead><tr><th scope="col">Osoba</th>{data.days.map(day => <th key={day} scope="col"><time dateTime={day}>{weekdayLabel(day)}</time></th>)}<th scope="col">Razem</th></tr></thead>
            <tbody>{data.rows.map(row => <tr key={row.userId}><th scope="row">{row.displayName}</th>
              {row.seconds.map((seconds, index) => <td key={data.days[index]} className="history-duration">{seconds ? formatHours(seconds) : '–'}</td>)}
              <td className="history-duration"><strong>{formatHours(row.total)}</strong></td></tr>)}</tbody>
            <tfoot><tr><th scope="row">Razem</th>{data.dayTotals.map((seconds, index) => <td key={data.days[index]} className="history-duration">{seconds ? formatHours(seconds) : '–'}</td>)}
              <td className="history-duration"><strong>{formatHours(data.total)}</strong></td></tr></tfoot>
          </table>
        </div>
        {data.truncated && <p className="field-hint">Karta jest niepełna, bo zakres ma bardzo dużo wpisów. Zawęź filtry.</p>}
      </>}</ReportState>}
  </section>
}

function ActivePanel({ userId, team }: { userId: string; team: Team }) {
  const [page, setPage] = useState(0)
  const visible = team.activeTimersVisible
  const active = useReportActive(userId, team.id, page, visible)
  return <section className="report-section report-active" aria-label="Kto teraz pracuje">
    <h3>Kto teraz pracuje</h3>
    {!visible ? <p className="report-empty">Podgląd aktywnych timerów jest wyłączony w ustawieniach zespołu.</p>
      : <ReportState query={active}>{data => <>
        {data.data.content.length === 0 ? <p className="report-empty">Nikt nie ma teraz uruchomionego timera.</p>
          : <div className="team-table-wrap" tabIndex={0} role="region" aria-label="Tabela aktywnych timerów">
            <table className="team-table report-active-table"><caption className="sr-only">Uruchomione timery członków zespołu</caption>
              <thead><tr><th scope="col">Osoba</th><th scope="col">Projekt</th><th scope="col">Stan</th><th scope="col">Od</th><th scope="col">Czas pracy</th></tr></thead>
              <tbody>{data.data.content.map(({ entry, elapsedSeconds }) => <tr key={entry.id}>
                <td data-label="Osoba">{entry.displayName}</td>
                <td data-label="Projekt">{entry.projectName}{entry.projectArchivedAt && <span className="report-meta">Projekt archiwalny</span>}</td>
                <td data-label="Stan">{entry.pausedAt ? 'Wstrzymany' : 'Pracuje'}</td>
                <td data-label="Od">{startedAt(entry.startedAt)}</td>
                <td data-label="Czas pracy" className="history-duration">{formatClock(elapsedSeconds)}</td>
              </tr>)}</tbody>
            </table>
          </div>}
        <Pagination data={data.data} busy={active.isFetching} label="Strony aktywnych timerów" onPage={setPage} />
      </>}</ReportState>}
  </section>
}

export function OwnerDashboard({ userId, team }: { userId: string; team: Team }) {
  const [search, setSearch] = useSearchParams()
  // The address is the source of truth, so a report can be bookmarked, shared and reached with the back button.
  const filters = useMemo(() => filtersFromSearch(search, team.timezone, new Date(), team.weekStart), [search, team.timezone, team.weekStart])
  const [draft, setDraft] = useState(filters)
  const [validation, setValidation] = useState('')
  const [memberPage, setMemberPage] = useState(0)
  const [projectPage, setProjectPage] = useState(0)
  const [entryPage, setEntryPage] = useState(0)
  const [dayPage, setDayPage] = useState(0)
  const [mixedPage, setMixedPage] = useState(0)
  const [sort, setSort] = useState<ReportSort>('WORK_DATE:DESC')
  const summary = useReportSummary(userId, team.id, filters)
  const people = useReportBreakdown(userId, team.id, filters, 'MEMBER', memberPage)
  const projects = useReportBreakdown(userId, team.id, filters, 'PROJECT', projectPage)
  const days = useReportBreakdown(userId, team.id, filters, 'DAY', dayPage)
  const mixed = useReportBreakdown(userId, team.id, filters, 'MEMBER_PROJECT', mixedPage)
  const entries = useReportEntries(userId, team.id, filters, sort, entryPage)
  const csv = useDownloadReport(team.id, filters, 'csv')
  const pdf = useDownloadReport(team.id, filters, 'pdf')
  const memberOptions = useReportMembers(userId, team.id)
  const tagOptions = useTags(userId, team.id, true)
  const jira = useJiraStatus(userId, team.id)
  const jiraConnected = jira.data?.siteConnected === true
  const xlsx = useDownloadReport(team.id, filters, 'xlsx')
  const projectOptions = useReportProjects(userId, team.id)
  const queries = [summary, people, projects, days, mixed, entries, memberOptions, projectOptions]
  const accessError = queries.map(query => query.error).find(error => error instanceof ApiError && [401, 403, 404].includes(error.status))
  const busy = queries.some(query => query.isFetching)
  // A failed background refresh must never leave previously authorized data visible.
  const members = accessError || memberOptions.isError ? [] : memberOptions.data?.pages.flatMap(page => page.data.content) ?? []
  const profiles = new Map(members.flatMap(member => { const text = [member.jobTitle, member.ftePercent ? `${member.ftePercent}% etatu` : null].filter(Boolean).join(' · '); return text ? [[member.userId, text] as [string, string]] : [] }))
  const options = accessError || projectOptions.isError ? [] : projectOptions.data?.pages.flatMap(page => page.data.content) ?? []

  function resetPages() { setMemberPage(0); setProjectPage(0); setEntryPage(0); setDayPage(0); setMixedPage(0) }
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
  const ranges = reportRanges(team.timezone, new Date(), team.weekStart)

  return <section className="owner-dashboard" aria-label="Raport zespołu">
    <header className="teams-toolbar">
      <div><h2><TeamMark team={team} />Czas zespołu</h2><p>Zapisana praca w zespole {team.name}.</p></div>
      <div className="report-actions">
        <button className="button-secondary" disabled={csv.isPending} onClick={() => csv.mutate()}>
          {csv.isPending ? <LoaderCircle size={15} className="spin" /> : <Download size={15} />}Pobierz CSV
        </button>
        <button className="button-secondary" disabled={xlsx.isPending} onClick={() => xlsx.mutate()}>
          {xlsx.isPending ? <LoaderCircle size={15} className="spin" /> : <Download size={15} />}Pobierz Excel
        </button>
        <button className="button-secondary" disabled={pdf.isPending} onClick={() => pdf.mutate()}>
          {pdf.isPending ? <LoaderCircle size={15} className="spin" /> : <Download size={15} />}Pobierz PDF
        </button>
        <button className="button-secondary" disabled={busy} onClick={() => void Promise.all(queries.map(query => query.refetch()))}>
          <RefreshCw size={15} className={busy ? 'spin' : ''} />Odśwież raport
        </button>
      </div>
    </header>
    {(csv.isError || pdf.isError || xlsx.isError) && <p className="form-error" role="alert">{(csv.error ?? pdf.error ?? xlsx.error)?.message}</p>}
    <ActivePanel userId={userId} team={team} />
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
      {(tagOptions.data?.length ?? 0) > 0 && <div className="field-group"><label htmlFor="report-tag">Tag</label>
        <select className="text-input" id="report-tag" value={draft.tagId ?? ''} onChange={event => setDraft({ ...draft, tagId: event.target.value })}>
          <option value="">Wszystkie tagi</option>{tagOptions.data!.map(tag => <option key={tag.id} value={tag.id}>{tag.name}{tag.archivedAt ? ' (archiwalny)' : ''}</option>)}
        </select></div>}
      {jiraConnected && <div className="field-group"><label htmlFor="report-issue">Zadanie Jira (klucz)</label>
        <input className="text-input" id="report-issue" maxLength={64} placeholder="np. PROJ-123" value={draft.issueKey ?? ''} onChange={event => setDraft({ ...draft, issueKey: event.target.value })} /></div>}
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
        <section className="report-section" aria-label="Czas według osób"><h3>Czas według osób</h3><ReportState query={people}>{data => <BreakdownTable data={data.data} group="MEMBER" busy={people.isFetching} onPage={setMemberPage} profiles={profiles} />}</ReportState></section>
        <section className="report-section" aria-label="Czas według projektów"><h3>Czas według projektów</h3><ReportState query={projects}>{data => <>{complete(data.data) && <BarChart title="Podział czasu na projekty" layout="rows" labelHeading="Projekt" items={projectChart(data.data)} />}<BreakdownTable data={data.data} group="PROJECT" busy={projects.isFetching} onPage={setProjectPage} /></>}</ReportState></section>
        <section className="report-section" aria-label="Czas według dni"><h3>Czas według dni</h3><ReportState query={days}>{data => <>{complete(data.data) && <BarChart title="Czas w kolejnych dniach" labelHeading="Dzień" items={dayChart(data.data)} />}<BreakdownTable data={data.data} group="DAY" busy={days.isFetching} onPage={setDayPage} /></>}</ReportState></section>
        {(tagOptions.data?.length ?? 0) > 0 && <TagBreakdown userId={userId} team={team} filters={filters} />}
        {jiraConnected && <JiraBreakdown userId={userId} team={team} filters={filters} />}
        <section className="report-section" aria-label="Czas według osób i projektów"><h3>Osoby i projekty</h3><ReportState query={mixed}>{data => <BreakdownTable data={data.data} group="MEMBER_PROJECT" busy={mixed.isFetching} onPage={setMixedPage} profiles={profiles} />}</ReportState></section>
      </div>
      <TimesheetPanel userId={userId} team={team} filters={filters} />
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
                  <td data-label="Projekt i opis pracy"><strong className="history-project-name"><ProjectDot color={entry.projectColor} />{entry.projectName}</strong>{entry.projectArchivedAt && <span className="report-meta">Projekt archiwalny</span>}{entry.tagName && <span className="tag-mark">{entry.tagName}</span>}<p className="history-description">{entry.description || 'Bez opisu'}</p><JiraKeyLink userId={userId} teamId={team.id} issueKey={entry.jiraIssueKey} />{entry.editedAt && <span className="edited-mark" title={`Ostatnio zmieniony ${new Date(entry.editedAt).toLocaleString('pl-PL', { dateStyle: 'medium', timeStyle: 'short' })}`}>{entry.editedByName ? `poprawił(a): ${entry.editedByName}` : 'edytowano'}</span>}</td>
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
