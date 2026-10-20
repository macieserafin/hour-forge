import { useEffect, useId, useState } from 'react'
import { LoaderCircle, Search, X } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { JiraIssueFilters } from '../lib/api'
import { useJiraIssues, useJiraStatus } from '../lib/jira'

export interface JiraIssueLink {
  id: string
  key: string
  summary: string
}

interface Props {
  userId: string
  teamId: string
  value: JiraIssueLink | null
  onChange: (issue: JiraIssueLink | null) => void
  disabled?: boolean
}

function IssueSearch({ userId, teamId, onPick }: { userId: string; teamId: string; onPick: (issue: JiraIssueLink) => void }) {
  const ids = useId()
  const [text, setText] = useState('')
  const [debounced, setDebounced] = useState('')
  const [assignee, setAssignee] = useState<JiraIssueFilters['assignee']>('ME')
  const [sprint, setSprint] = useState<JiraIssueFilters['sprint']>('ANY')
  const [includeDone, setIncludeDone] = useState(false)

  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(text), 300)
    return () => window.clearTimeout(timer)
  }, [text])

  const issues = useJiraIssues(userId, teamId, { q: debounced, assignee, sprint, includeDone }, true)
  const list = issues.data?.pages.flatMap(page => page.issues) ?? []

  return <div className="jira-picker-panel">
    <div className="field-group"><label htmlFor={`${ids}-q`}>Szukaj zadania</label>
      <div className="jira-search"><Search size={16} aria-hidden="true" /><input id={`${ids}-q`} className="text-input" type="search" maxLength={100} placeholder="Klucz lub fragment tytułu" value={text} onChange={event => setText(event.target.value)} /></div></div>
    <div className="jira-filters">
      <div className="field-group"><label htmlFor={`${ids}-assignee`}>Zadania</label>
        <select id={`${ids}-assignee`} className="text-input" value={assignee} onChange={event => setAssignee(event.target.value as typeof assignee)}><option value="ME">Przypisane do mnie</option><option value="ANYONE">Wszystkie</option></select></div>
      <div className="field-group"><label htmlFor={`${ids}-sprint`}>Sprint</label>
        <select id={`${ids}-sprint`} className="text-input" value={sprint} onChange={event => setSprint(event.target.value as typeof sprint)}><option value="ANY">Dowolny</option><option value="ACTIVE">Aktywny sprint</option><option value="CLOSED">Zakończone sprinty</option></select></div>
    </div>
    <label className="jira-checkbox"><input type="checkbox" checked={includeDone} onChange={event => setIncludeDone(event.target.checked)} />Pokaż także ukończone</label>
    <div role="status" className="field-hint">{issues.isPending ? 'Szukanie zadań…' : issues.isError ? '' : list.length === 0 ? 'Nie znaleziono zadań.' : `Znaleziono zadań: ${list.length}${issues.hasNextPage ? '+' : ''}`}</div>
    {issues.isError && <div className="dashboard-error" role="alert">{issues.error.message}<button type="button" onClick={() => void issues.refetch()}>Spróbuj ponownie</button></div>}
    {issues.isPending && <p className="teams-loading"><LoaderCircle size={18} className="spin" aria-hidden="true" /></p>}
    {list.length > 0 && <ul className="jira-issue-list" aria-label="Wyniki wyszukiwania zadań Jira">
      {list.map(issue => <li key={issue.id}><button type="button" className="jira-issue-option" onClick={() => onPick({ id: issue.id, key: issue.key, summary: issue.summary })}>
        <strong>{issue.key}</strong>{' '}<span className="jira-issue-summary">{issue.summary}</span>{' '}<span className="jira-issue-meta">{issue.issueType} · {issue.status}</span></button></li>)}
    </ul>}
    {issues.hasNextPage && <button type="button" className="button-secondary" disabled={issues.isFetchingNextPage} onClick={() => void issues.fetchNextPage()}>{issues.isFetchingNextPage && <LoaderCircle size={16} className="spin" />}Pokaż więcej zadań</button>}
  </div>
}

/** Optional link of a time entry to a Jira issue. Shows nothing unless the team has Jira and the person is connected. */
export function JiraIssuePicker({ userId, teamId, value, onChange, disabled }: Props) {
  const status = useJiraStatus(userId, teamId)
  const [open, setOpen] = useState(false)
  if (!status.data?.available || !status.data.siteConnected) return null

  if (!status.data.userConnected) {
    return <p className="field-hint jira-hint">Aby powiązać wpis z zadaniem, <Link className="text-link" to={`/integrations?team=${encodeURIComponent(teamId)}`}>połącz swoje konto Jira</Link>.</p>
  }

  return <div className="jira-picker">
    <span className="jira-picker-label" id={`jira-label-${teamId}`}>Zadanie Jira <span className="optional-label">(opcjonalnie)</span></span>
    {value ? <div className="jira-selected" aria-labelledby={`jira-label-${teamId}`}>
      <strong>{value.key}</strong><span className="jira-issue-summary">{value.summary}</span>
      <button type="button" className="icon-button" disabled={disabled} aria-label={`Usuń powiązanie z zadaniem ${value.key}`} onClick={() => onChange(null)}><X size={16} /></button>
    </div> : <>
      <button type="button" className="button-secondary" disabled={disabled} aria-expanded={open} onClick={() => setOpen(!open)}>{open ? 'Zwiń wyszukiwarkę' : 'Wybierz zadanie z Jiry'}</button>
      {open && <IssueSearch userId={userId} teamId={teamId} onPick={issue => { onChange(issue); setOpen(false) }} />}
    </>}
  </div>
}
