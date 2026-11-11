import { useState, type FormEvent } from 'react'
import { ArrowLeft, ChevronLeft, ChevronRight, Download, Filter, Radio, Timer, X } from 'lucide-react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { useI18n, useMessages } from '../../content/i18n'
import { formatDuration, formatWorkDate } from '../../domain/duration'
import { useUser } from '../../app/session'
import {
  canManage,
  type ReportBucket,
  type ReportDownload,
  type ReportFilters,
  type ReportGroup,
  type ReportSort,
  type Team,
  type TeamMember,
} from '../../lib/api'
import { useJiraStatus } from '../../lib/jira'
import { usePreferences } from '../../lib/preferences'
import { projectColorHex } from '../../lib/projectColors'
import {
  clearedSearch,
  filtersFromSearch,
  filtersToSearch,
  MAX_REPORT_DAYS,
  reportRangeDays,
  reportRanges,
  useDownloadReport,
  useReportActive,
  useReportBreakdown,
  useReportEntries,
  useReportMembers,
  useReportProjects,
  useReportSummary,
  useReportTimesheet,
} from '../../lib/reports'
import { useTags } from '../../lib/tags'
import { useTeams } from '../../lib/teams'
import { Button, Dot, Empty, Field, LoadError, Loading, Notice } from '../../ui'
import { CorrectionsDialog } from '../teams/People'
import './reports.css'

const messages = {
  pl: {
    period: 'Okres',
    title: 'Raport zespołu',
    back: 'Zespół',
    today: 'Dzisiaj',
    week: 'Ten tydzień',
    lastWeek: 'Poprzedni tydzień',
    month: 'Ten miesiąc',
    undated: 'Bez daty',
    custom: 'Inny zakres',
    from: 'Od',
    to: 'Do',
    apply: 'Pokaż',
    filters: 'Filtry',
    hideFilters: 'Ukryj filtry',
    clear: 'Wyczyść filtry',
    person: 'Osoba',
    project: 'Projekt',
    tag: 'Tag',
    issue: 'Zadanie Jira (klucz)',
    type: 'Rodzaj wpisu',
    anyType: 'Wszystkie',
    timer: 'Timer',
    manual: 'Ręczne',
    search: 'Szukaj w opisach',
    anyone: 'Wszystkie osoby',
    anyProject: 'Wszystkie projekty',
    anyTag: 'Wszystkie tagi',
    more: 'Wczytaj kolejne',
    badRange: 'Zakres może mieć najwyżej {days} dni, a „od” nie może być późniejsze niż „do”.',
    total: 'Łączny czas',
    entries: 'Wpisy',
    active: 'Aktywne filtry:',
    remove: 'Usuń filtr: {name}',
    breakdown: 'Podział',
    MEMBER: 'Osoby',
    PROJECT: 'Projekty',
    DAY: 'Dni',
    MEMBER_PROJECT: 'Osoba × projekt',
    TAG: 'Tagi',
    JIRA_ISSUE: 'Zadania Jira',
    noTag: 'Bez tagu',
    noIssue: 'Bez zadania',
    drill: 'Pokaż wpisy: {name}',
    share: '{percent}%',
    none: 'Brak danych w wybranym zakresie.',
    timesheet: 'Karta czasu osoba × dzień',
    truncated: 'Karta pokazuje część okresu; zawęź zakres, aby zobaczyć całość.',
    list: 'Wpisy',
    sort: 'Kolejność',
    'WORK_DATE:DESC': 'Najnowsze daty',
    'WORK_DATE:ASC': 'Najstarsze daty',
    'DURATION:DESC': 'Najdłuższe',
    'DURATION:ASC': 'Najkrótsze',
    'CREATED_AT:DESC': 'Ostatnio dodane',
    'CREATED_AT:ASC': 'Najdawniej dodane',
    correct: 'Korekty: {name}',
    prev: 'Poprzednia',
    next: 'Następna',
    page: 'Strona {page}',
    export: 'Eksportuj',
    csv: 'CSV',
    xlsx: 'Excel',
    pdf: 'PDF',
    exportNote: 'Eksport zawiera te same filtry co raport.',
    live: 'Kto teraz pracuje',
    liveOff: 'Podgląd jest wyłączony w ustawieniach zespołu.',
    liveNone: 'Nikt nie mierzy teraz czasu.',
    paused: 'przerwa',
    since: 'od {time}',
    noAccess: 'Raport zespołu widzą właściciel i administratorzy. Swoją pracę zobaczysz w raporcie własnym.',
    mine: 'Mój raport',
    notFound: 'Nie znaleziono zespołu.',
    edited: 'poprawił(a): {name}',
    dateRange: '{from} – {to}',
  },
  en: {
    period: 'Period',
    title: 'Team report',
    back: 'Team',
    today: 'Today',
    week: 'This week',
    lastWeek: 'Last week',
    month: 'This month',
    undated: 'No date',
    custom: 'Custom range',
    from: 'From',
    to: 'To',
    apply: 'Show',
    filters: 'Filters',
    hideFilters: 'Hide filters',
    clear: 'Clear filters',
    person: 'Person',
    project: 'Project',
    tag: 'Tag',
    issue: 'Jira issue (key)',
    type: 'Entry type',
    anyType: 'All',
    timer: 'Timer',
    manual: 'Manual',
    search: 'Search descriptions',
    anyone: 'Everyone',
    anyProject: 'All projects',
    anyTag: 'All tags',
    more: 'Load more',
    badRange: 'The range can have at most {days} days and "from" cannot be later than "to".',
    total: 'Total time',
    entries: 'Entries',
    active: 'Active filters:',
    remove: 'Remove filter: {name}',
    breakdown: 'Breakdown',
    MEMBER: 'People',
    PROJECT: 'Projects',
    DAY: 'Days',
    MEMBER_PROJECT: 'Person × project',
    TAG: 'Tags',
    JIRA_ISSUE: 'Jira issues',
    noTag: 'No tag',
    noIssue: 'No issue',
    drill: 'Show entries: {name}',
    share: '{percent}%',
    none: 'No data in the selected range.',
    timesheet: 'Timesheet person × day',
    truncated: 'The sheet shows part of the period; narrow the range to see all of it.',
    list: 'Entries',
    sort: 'Order',
    'WORK_DATE:DESC': 'Newest dates',
    'WORK_DATE:ASC': 'Oldest dates',
    'DURATION:DESC': 'Longest',
    'DURATION:ASC': 'Shortest',
    'CREATED_AT:DESC': 'Recently added',
    'CREATED_AT:ASC': 'Earliest added',
    correct: 'Corrections: {name}',
    prev: 'Previous',
    next: 'Next',
    page: 'Page {page}',
    export: 'Export',
    csv: 'CSV',
    xlsx: 'Excel',
    pdf: 'PDF',
    exportNote: 'The export uses the same filters as the report.',
    live: 'Who is working now',
    liveOff: 'The live view is turned off in the team settings.',
    liveNone: 'Nobody is tracking time right now.',
    paused: 'paused',
    since: 'since {time}',
    noAccess: 'The team report is for the owner and admins. Your own work is in your report.',
    mine: 'My report',
    notFound: 'Team not found.',
    edited: 'edited by {name}',
    dateRange: '{from} – {to}',
  },
}

const groups: ReportGroup[] = ['MEMBER', 'PROJECT', 'DAY', 'MEMBER_PROJECT', 'TAG', 'JIRA_ISSUE']

/** Team report with ranges and filters kept in the address, drill-down from any group to its entries, exports. */
export function TeamReport() {
  const user = useUser()
  const m = useMessages(messages)
  const { teamId = '' } = useParams()
  const teams = useTeams(user.id)
  const team = teams.data?.find(item => item.id === teamId)
  if (teams.isPending)
    return (
      <div className="page">
        <Loading />
      </div>
    )
  if (!team)
    return (
      <div className="page">
        <Empty
          title={m('notFound')}
          action={
            <Link className="btn" to="/app/teams">
              {m('back')}
            </Link>
          }
        />
      </div>
    )
  if (!canManage(team.role))
    return (
      <div className="page">
        <Empty
          title={m('title')}
          action={
            <Link className="btn btn-primary" to="/app/reports">
              {m('mine')}
            </Link>
          }
        >
          {m('noAccess')}
        </Empty>
      </div>
    )
  return <Report team={team} />
}

function Report({ team }: { team: Team }) {
  const user = useUser()
  const { locale } = useI18n()
  const m = useMessages(messages)
  const weekStart = usePreferences(user.id).data?.weekStart ?? team.weekStart
  const [search, setSearch] = useSearchParams()
  const filters = filtersFromSearch(search, team.timezone, new Date(), weekStart)
  const ranges = reportRanges(team.timezone, new Date(), weekStart)
  const [showFilters, setShowFilters] = useState(false)
  const [group, setGroup] = useState<ReportGroup>('MEMBER')
  const [groupPage, setGroupPage] = useState(0)
  const [sort, setSort] = useState<ReportSort>('WORK_DATE:DESC')
  const [entryPage, setEntryPage] = useState(0)
  const [correcting, setCorrecting] = useState<TeamMember | null>(null)
  const apply = (next: ReportFilters) => {
    setSearch(filtersToSearch(next, search), { replace: true })
    setGroupPage(0)
    setEntryPage(0)
  }
  const summary = useReportSummary(user.id, team.id, filters)
  const breakdown = useReportBreakdown(user.id, team.id, filters, group, groupPage)
  const entries = useReportEntries(user.id, team.id, filters, sort, entryPage)
  const timesheet = useReportTimesheet(user.id, team.id, filters, !filters.undated)
  const members = useReportMembers(user.id, team.id)
  const projects = useReportProjects(user.id, team.id)
  const tags = useTags(user.id, team.id, true)
  const memberName = (id: string) =>
    members.data?.pages.flatMap(page => page.data.content).find(item => item.userId === id)?.displayName ?? '…'
  const projectName = (id: string) =>
    projects.data?.pages.flatMap(page => page.data.content).find(item => item.projectId === id)?.name ?? '…'
  const preset = filters.undated
    ? 'undated'
    : (Object.entries(ranges).find(([, range]) => range.from === filters.from && range.to === filters.to)?.[0] ??
      'custom')
  const chips = [
    filters.userId && { key: 'userId', label: `${m('person')}: ${memberName(filters.userId)}` },
    filters.projectId && { key: 'projectId', label: `${m('project')}: ${projectName(filters.projectId)}` },
    filters.tagId && {
      key: 'tagId',
      label: `${m('tag')}: ${tags.data?.find(tag => tag.id === filters.tagId)?.name ?? '…'}`,
    },
    filters.issueKey && { key: 'issueKey', label: `${m('issue')}: ${filters.issueKey}` },
    filters.entryType && {
      key: 'entryType',
      label: `${m('type')}: ${m(filters.entryType === 'TIMER' ? 'timer' : 'manual')}`,
    },
    filters.q && { key: 'q', label: `${m('search')}: ${filters.q}` },
  ].filter(Boolean) as { key: keyof ReportFilters; label: string }[]

  return (
    <div className="page report">
      <header className="page-head">
        <div>
          <Link className="back-link" to={`/app/teams/${encodeURIComponent(team.id)}`}>
            <ArrowLeft size={16} aria-hidden="true" />
            {team.name}
          </Link>
          <h1>{m('title')}</h1>
          <p className="muted">
            {filters.undated
              ? m('undated')
              : m('dateRange', {
                  from: formatWorkDate(filters.from, locale),
                  to: formatWorkDate(filters.to, locale),
                })}{' '}
            · {team.timezone}
          </p>
        </div>
        <Exports team={team} filters={filters} />
      </header>

      <section className="card stack report-controls" aria-label={m('filters')}>
        <div className="toolbar">
          <label className="work-scope">
            <span className="field-label">{m('period')}</span>
            <select
              className="select input"
              value={preset}
              onChange={event => {
                const value = event.target.value as keyof typeof ranges | 'undated' | 'custom'
                if (value === 'custom') {
                  if (filters.undated) apply({ ...filters, undated: false, ...ranges.week })
                  setShowFilters(true)
                } else if (value === 'undated') apply({ ...filters, undated: true, from: '', to: '' })
                else apply({ ...filters, undated: false, ...ranges[value] })
              }}
            >
              {(['today', 'week', 'lastWeek', 'month', 'undated', 'custom'] as const).map(key => (
                <option key={key} value={key}>
                  {m(key)}
                </option>
              ))}
            </select>
          </label>
          <Button small onClick={() => setShowFilters(!showFilters)} aria-expanded={showFilters}>
            <Filter size={15} aria-hidden="true" />
            {m(showFilters ? 'hideFilters' : 'filters')}
          </Button>
        </div>
        {showFilters && (
          <FilterForm
            key={search.toString()}
            filters={filters}
            team={team}
            onApply={apply}
            memberQuery={members}
            projectQuery={projects}
            tags={tags.data ?? []}
          />
        )}
        {!!chips.length && (
          <div className="chips" role="group" aria-label={m('active')}>
            <span className="muted">{m('active')}</span>
            {chips.map(chip => (
              <button
                key={chip.key}
                type="button"
                className="chip"
                aria-label={m('remove', { name: chip.label })}
                onClick={() => apply({ ...filters, [chip.key]: '' })}
              >
                {chip.label}
                <X size={14} aria-hidden="true" />
              </button>
            ))}
            <button
              type="button"
              className="link-btn"
              onClick={() => {
                setSearch(clearedSearch(search), { replace: true })
                setGroupPage(0)
                setEntryPage(0)
              }}
            >
              {m('clear')}
            </button>
          </div>
        )}
      </section>

      <section className="totals" aria-label={m('total')}>
        <div className="stat stat-primary">
          <span>{m('total')}</span>
          <b>{summary.data ? formatDuration(summary.data.totals.durationSeconds) : '—'}</b>
        </div>
        <div className="stat">
          <span>{m('entries')}</span>
          <b className="num">{summary.data?.totals.entryCount ?? '—'}</b>
        </div>
        <LiveCard team={team} />
      </section>
      {summary.isError && <LoadError onRetry={() => void summary.refetch()} message={summary.error.message} />}

      <section className="card stack" aria-labelledby="breakdown-title">
        <div className="card-head">
          <h2 id="breakdown-title">{m('breakdown')}</h2>
        </div>
        <select
          className="select input report-group"
          aria-labelledby="breakdown-title"
          value={group}
          onChange={event => {
            setGroup(event.target.value as ReportGroup)
            setGroupPage(0)
          }}
        >
          {groups.map(item => (
            <option key={item} value={item}>
              {m(item)}
            </option>
          ))}
        </select>
        {breakdown.isPending ? (
          <div className="skeleton" style={{ height: 160 }} />
        ) : breakdown.isError ? (
          <LoadError onRetry={() => void breakdown.refetch()} message={breakdown.error.message} />
        ) : !breakdown.data.data.content.length ? (
          <p className="muted">{m('none')}</p>
        ) : (
          <Buckets
            buckets={breakdown.data.data.content}
            group={group}
            total={summary.data?.totals.durationSeconds ?? 0}
            onDrill={bucket => apply(drill(filters, group, bucket))}
          />
        )}
        {breakdown.data && (groupPage > 0 || breakdown.data.data.hasNext) && (
          <Pager
            page={groupPage}
            hasNext={breakdown.data.data.hasNext}
            onPage={setGroupPage}
            busy={breakdown.isFetching}
          />
        )}
      </section>

      {!filters.undated && (
        <section className="card stack" aria-labelledby="sheet-title">
          <div className="card-head">
            <h2 id="sheet-title">{m('timesheet')}</h2>
          </div>
          {timesheet.isPending ? (
            <div className="skeleton" style={{ height: 120 }} />
          ) : timesheet.isError ? (
            <LoadError onRetry={() => void timesheet.refetch()} message={timesheet.error.message} />
          ) : !timesheet.data.rows.length ? (
            <p className="muted">{m('none')}</p>
          ) : (
            <>
              {timesheet.data.truncated && <Notice tone="info">{m('truncated')}</Notice>}
              <div className="table-scroll" role="region" aria-labelledby="sheet-title" tabIndex={0}>
                <table className="table sheet">
                  <thead>
                    <tr>
                      <th scope="col">{m('person')}</th>
                      {timesheet.data.days.map(day => (
                        <th scope="col" key={day}>
                          {formatWorkDate(day, locale, true)}
                        </th>
                      ))}
                      <th scope="col">{m('total')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {timesheet.data.rows.map(row => (
                      <tr key={row.userId}>
                        <th scope="row">
                          <button
                            type="button"
                            className="link-btn"
                            onClick={() => apply({ ...filters, userId: row.userId })}
                          >
                            {row.displayName}
                          </button>
                        </th>
                        {row.seconds.map((seconds, index) => (
                          <td key={index} className="num">
                            {seconds ? formatDuration(seconds) : '—'}
                          </td>
                        ))}
                        <td className="num strong">{formatDuration(row.total)}</td>
                      </tr>
                    ))}
                  </tbody>
                  <tfoot>
                    <tr>
                      <th scope="row">{m('total')}</th>
                      {timesheet.data.dayTotals.map((seconds, index) => (
                        <td key={index} className="num">
                          {seconds ? formatDuration(seconds) : '—'}
                        </td>
                      ))}
                      <td className="num strong">{formatDuration(timesheet.data.total)}</td>
                    </tr>
                  </tfoot>
                </table>
              </div>
            </>
          )}
        </section>
      )}

      <section className="card stack" aria-labelledby="entries-title">
        {!!chips.length && (
          <p className="field-hint">
            {m('active')} {chips.map(chip => chip.label).join(' · ')}
          </p>
        )}
        <div className="card-head">
          <h2 id="entries-title">{m('list')}</h2>
          <label className="inline-select">
            <span className="muted">{m('sort')}</span>
            <select
              className="select input"
              value={sort}
              onChange={event => {
                setSort(event.target.value as ReportSort)
                setEntryPage(0)
              }}
            >
              {(
                [
                  'WORK_DATE:DESC',
                  'WORK_DATE:ASC',
                  'DURATION:DESC',
                  'DURATION:ASC',
                  'CREATED_AT:DESC',
                  'CREATED_AT:ASC',
                ] as const
              ).map(item => (
                <option key={item} value={item}>
                  {m(item)}
                </option>
              ))}
            </select>
          </label>
        </div>
        {entries.isPending ? (
          <Loading />
        ) : entries.isError ? (
          <LoadError onRetry={() => void entries.refetch()} message={entries.error.message} />
        ) : !entries.data.data.content.length ? (
          <p className="muted">{m('none')}</p>
        ) : (
          <ul className="entries">
            {entries.data.data.content.map(entry => (
              <li key={entry.id}>
                <Dot color={projectColorHex(entry.projectColor)} />
                <span className="entry-main">
                  <b>{entry.displayName}</b>
                  <span>{entry.projectName}</span>
                  <small>
                    {entry.description || '—'}
                    {entry.tagName ? ` · ${entry.tagName}` : ''}
                    {entry.jiraIssueKey ? ` · ${entry.jiraIssueKey}` : ''} ·{' '}
                    {m(entry.entryType === 'TIMER' ? 'timer' : 'manual')}
                    {entry.editedByName ? ` · ${m('edited', { name: entry.editedByName })}` : ''}
                  </small>
                </span>
                <span className="entry-side">
                  <b className="num">{formatDuration(entry.durationSeconds)}</b>
                  <small>{entry.workDate ? formatWorkDate(entry.workDate, locale) : m('undated')}</small>
                </span>
                {!team.archivedAt && entry.userId !== user.id && (
                  <button
                    type="button"
                    className="icon-btn"
                    aria-label={m('correct', { name: entry.displayName })}
                    onClick={() =>
                      setCorrecting({
                        userId: entry.userId,
                        displayName: entry.displayName,
                        role: 'MEMBER',
                        joinedAt: '',
                        leftAt: null,
                      })
                    }
                  >
                    <Timer size={16} />
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}
        {entries.data && (entryPage > 0 || entries.data.data.hasNext) && (
          <Pager page={entryPage} hasNext={entries.data.data.hasNext} onPage={setEntryPage} busy={entries.isFetching} />
        )}
      </section>
      {correcting && <CorrectionsDialog team={team} member={correcting} onClose={() => setCorrecting(null)} />}
    </div>
  )
}

/** Narrows the filters to one group of the breakdown (from a summary to its entries). */
function drill(filters: ReportFilters, group: ReportGroup, bucket: ReportBucket): ReportFilters {
  switch (group) {
    case 'MEMBER':
      return { ...filters, userId: bucket.userId ?? '' }
    case 'PROJECT':
      return { ...filters, projectId: bucket.projectId ?? '' }
    case 'MEMBER_PROJECT':
      return { ...filters, userId: bucket.userId ?? '', projectId: bucket.projectId ?? '' }
    case 'DAY':
      return bucket.workDate
        ? { ...filters, undated: false, from: bucket.workDate, to: bucket.workDate }
        : { ...filters, undated: true, from: '', to: '' }
    case 'TAG':
      return { ...filters, tagId: bucket.tagId ?? '' }
    case 'JIRA_ISSUE':
      return { ...filters, issueKey: bucket.jiraIssueKey ?? '' }
  }
}

function Buckets({
  buckets,
  group,
  total,
  onDrill,
}: {
  buckets: ReportBucket[]
  group: ReportGroup
  total: number
  onDrill: (bucket: ReportBucket) => void
}) {
  const { locale } = useI18n()
  const m = useMessages(messages)
  const max = Math.max(1, ...buckets.map(bucket => bucket.durationSeconds))
  const name = (bucket: ReportBucket) =>
    group === 'MEMBER'
      ? bucket.displayName
      : group === 'PROJECT'
        ? bucket.projectName
        : group === 'MEMBER_PROJECT'
          ? `${bucket.displayName} · ${bucket.projectName}`
          : group === 'DAY'
            ? bucket.workDate
              ? formatWorkDate(bucket.workDate, locale, true)
              : m('undated')
            : group === 'TAG'
              ? (bucket.tagName ?? m('noTag'))
              : (bucket.jiraIssueKey ?? m('noIssue'))
  const color = (bucket: ReportBucket) =>
    group === 'PROJECT' || group === 'MEMBER_PROJECT'
      ? projectColorHex(bucket.projectColor)
      : group === 'TAG'
        ? projectColorHex(bucket.tagColor)
        : undefined
  return (
    <ul className="buckets">
      {buckets.map((bucket, index) => {
        const label = name(bucket) ?? '—'
        return (
          <li key={index}>
            <button
              type="button"
              onClick={() => onDrill(bucket)}
              aria-label={`${m('drill', { name: label })}, ${formatDuration(bucket.durationSeconds)}`}
            >
              <span className="bucket-name">
                {color(bucket) && <Dot color={color(bucket)} />}
                {label}
              </span>
              <span className="bucket-bar" aria-hidden="true">
                <span style={{ width: `${(bucket.durationSeconds / max) * 100}%` }} />
              </span>
              <span className="bucket-value num">{formatDuration(bucket.durationSeconds)}</span>
              <span className="bucket-share muted">
                {total ? m('share', { percent: Math.round((bucket.durationSeconds / total) * 100) }) : ''}
              </span>
            </button>
          </li>
        )
      })}
    </ul>
  )
}

function Pager({
  page,
  hasNext,
  onPage,
  busy,
}: {
  page: number
  hasNext: boolean
  onPage: (page: number) => void
  busy: boolean
}) {
  const m = useMessages(messages)
  return (
    <nav className="pager" aria-label={m('page', { page: page + 1 })}>
      <Button small disabled={page === 0 || busy} onClick={() => onPage(page - 1)}>
        <ChevronLeft size={15} aria-hidden="true" />
        {m('prev')}
      </Button>
      <span>{m('page', { page: page + 1 })}</span>
      <Button small disabled={!hasNext || busy} onClick={() => onPage(page + 1)}>
        {m('next')}
        <ChevronRight size={15} aria-hidden="true" />
      </Button>
    </nav>
  )
}

type Options<T> = {
  data?: { pages: { data: { content: T[] } }[] }
  hasNextPage: boolean
  isFetchingNextPage: boolean
  fetchNextPage: () => unknown
}

function FilterForm({
  filters,
  team,
  onApply,
  memberQuery,
  projectQuery,
  tags,
}: {
  filters: ReportFilters
  team: Team
  onApply: (filters: ReportFilters) => void
  memberQuery: Options<TeamMember>
  projectQuery: Options<{ projectId: string; name: string }>
  tags: { id: string; name: string }[]
}) {
  const m = useMessages(messages)
  const jira = useJiraStatus(useUser().id, team.id)
  const [draft, setDraft] = useState(filters)
  const [error, setError] = useState('')
  const membersList = memberQuery.data?.pages.flatMap(page => page.data.content) ?? []
  const projectsList = projectQuery.data?.pages.flatMap(page => page.data.content) ?? []
  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (
      !draft.undated &&
      (!draft.from || !draft.to || draft.from > draft.to || reportRangeDays(draft.from, draft.to) > MAX_REPORT_DAYS)
    ) {
      setError(m('badRange', { days: MAX_REPORT_DAYS }))
      return
    }
    setError('')
    onApply(draft)
  }
  return (
    <form className="report-filters" onSubmit={submit} noValidate>
      {!draft.undated && (
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
      <Field label={m('person')}>
        {props => (
          <select
            {...props}
            className="select input"
            value={draft.userId}
            onChange={event => setDraft({ ...draft, userId: event.target.value })}
          >
            <option value="">{m('anyone')}</option>
            {membersList.map(member => (
              <option key={member.userId} value={member.userId}>
                {member.displayName}
              </option>
            ))}
          </select>
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
            <option value="">{m('anyProject')}</option>
            {projectsList.map(project => (
              <option key={project.projectId} value={project.projectId}>
                {project.name}
              </option>
            ))}
          </select>
        )}
      </Field>
      {(memberQuery.hasNextPage || projectQuery.hasNextPage) && (
        <div className="row-inline">
          {memberQuery.hasNextPage && (
            <Button small pending={memberQuery.isFetchingNextPage} onClick={() => void memberQuery.fetchNextPage()}>
              {m('more')}: {m('person')}
            </Button>
          )}
          {projectQuery.hasNextPage && (
            <Button small pending={projectQuery.isFetchingNextPage} onClick={() => void projectQuery.fetchNextPage()}>
              {m('more')}: {m('project')}
            </Button>
          )}
        </div>
      )}
      {!!tags.length && (
        <Field label={m('tag')}>
          {props => (
            <select
              {...props}
              className="select input"
              value={draft.tagId ?? ''}
              onChange={event => setDraft({ ...draft, tagId: event.target.value })}
            >
              <option value="">{m('anyTag')}</option>
              {tags.map(tag => (
                <option key={tag.id} value={tag.id}>
                  {tag.name}
                </option>
              ))}
            </select>
          )}
        </Field>
      )}
      {jira.data?.siteConnected && (
        <Field label={m('issue')}>
          {props => (
            <input
              {...props}
              className="input"
              value={draft.issueKey ?? ''}
              maxLength={64}
              placeholder="PROJ-12"
              onChange={event => setDraft({ ...draft, issueKey: event.target.value })}
            />
          )}
        </Field>
      )}
      <Field label={m('type')}>
        {props => (
          <select
            {...props}
            className="select input"
            value={draft.entryType}
            onChange={event => setDraft({ ...draft, entryType: event.target.value as ReportFilters['entryType'] })}
          >
            <option value="">{m('anyType')}</option>
            <option value="TIMER">{m('timer')}</option>
            <option value="MANUAL">{m('manual')}</option>
          </select>
        )}
      </Field>
      <Field label={m('search')}>
        {props => (
          <input
            {...props}
            className="input"
            type="search"
            maxLength={200}
            value={draft.q ?? ''}
            onChange={event => setDraft({ ...draft, q: event.target.value })}
          />
        )}
      </Field>
      <div className="filters-actions">
        <Button type="submit" variant="primary">
          {m('apply')}
        </Button>
      </div>
      {error && (
        <p className="field-error" role="alert">
          {error}
        </p>
      )}
    </form>
  )
}

function Exports({ team, filters }: { team: Team; filters: ReportFilters }) {
  const m = useMessages(messages)
  const kinds: ReportDownload[] = ['csv', 'xlsx', 'pdf']
  return (
    <details className="exports">
      <summary className="btn">{m('export')}</summary>
      <div className="row-inline" role="group" aria-label={m('export')}>
        {kinds.map(kind => (
          <ExportButton key={kind} team={team} filters={filters} kind={kind} />
        ))}
      </div>
      <p className="field-hint">{m('exportNote')}</p>
    </details>
  )
}

function ExportButton({ team, filters, kind }: { team: Team; filters: ReportFilters; kind: ReportDownload }) {
  const m = useMessages(messages)
  const download = useDownloadReport(team.id, filters, kind)
  return (
    <>
      <Button small pending={download.isPending} onClick={() => download.mutate()}>
        <Download size={15} aria-hidden="true" />
        {m(kind as 'csv' | 'xlsx' | 'pdf')}
      </Button>
      {download.isError && (
        <p className="field-error" role="alert">
          {download.error.message}
        </p>
      )}
    </>
  )
}

function LiveCard({ team }: { team: Team }) {
  const user = useUser()
  const { locale } = useI18n()
  const m = useMessages(messages)
  const active = useReportActive(user.id, team.id, 0, team.activeTimersVisible)
  const items = active.data?.data.content ?? []
  return (
    <div className="stat stat-outline live">
      <span>
        <Radio size={14} aria-hidden="true" /> {m('live')}
      </span>
      {!team.activeTimersVisible ? (
        <small>{m('liveOff')}</small>
      ) : active.isError ? (
        <small>{active.error.message}</small>
      ) : !items.length ? (
        <small>{m('liveNone')}</small>
      ) : (
        <ul className="live-list">
          {items.map(item => (
            <li key={item.entry.id}>
              <b>{item.entry.displayName}</b> <span className="muted">{item.entry.projectName}</span>
              <span className="num">
                {formatDuration(item.elapsedSeconds)}
                {item.entry.pausedAt ? ` · ${m('paused')}` : ''}
              </span>
              <small className="muted">
                {item.entry.startedAt
                  ? m('since', {
                      time: new Date(item.entry.startedAt).toLocaleTimeString(locale === 'pl' ? 'pl-PL' : 'en-GB', {
                        hour: '2-digit',
                        minute: '2-digit',
                        timeZone: team.timezone,
                      }),
                    })
                  : ''}
              </small>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
