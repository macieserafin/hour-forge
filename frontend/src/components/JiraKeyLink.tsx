import { useId, useState } from 'react'
import { useJiraIssue, useJiraStatus } from '../lib/jira'

const ISSUE_KEY = /^[A-Za-z][A-Za-z0-9_]{1,19}-[0-9]{1,9}$/

/**
 * The issue key of a linked entry; a link to the issue when the team's Jira address is known and looks safe. With the
 * issue id and a connected account, hovering or focusing the key shows the issue's title and state, read live from Jira.
 */
export function JiraKeyLink({ userId, teamId, issueKey, issueId }: { userId: string; teamId: string; issueKey: string | null | undefined; issueId?: string | null }) {
  const status = useJiraStatus(userId, teamId)
  const [open, setOpen] = useState(false)
  const tooltipId = useId()
  const canDescribe = !!issueId && status.data?.userConnected === true
  const issue = useJiraIssue(userId, teamId, issueId ?? '', open && canDescribe)
  if (!issueKey) return null
  const site = status.data?.siteUrl
  const common = canDescribe ? { onMouseEnter: () => setOpen(true), onMouseLeave: () => setOpen(false), onFocus: () => setOpen(true), onBlur: () => setOpen(false),
    onKeyDown: (event: React.KeyboardEvent) => { if (event.key === 'Escape') setOpen(false) }, 'aria-describedby': open ? tooltipId : undefined } : {}
  const key = site && site.startsWith('https://') && ISSUE_KEY.test(issueKey)
    ? <a className="jira-key" href={`${site.replace(/\/+$/, '')}/browse/${issueKey}`} target="_blank" rel="noopener noreferrer"
        aria-label={`Zadanie Jira ${issueKey} (otwiera się w nowej karcie)`} {...common}>{issueKey}</a>
    : <span className="jira-key" tabIndex={canDescribe ? 0 : undefined} {...common}>{issueKey}</span>
  if (!canDescribe) return key
  return <span className="jira-key-wrap">{key}
    {open && <span role="tooltip" id={tooltipId} className="jira-tooltip">
      {issue.isPending ? 'Wczytywanie zadania…' : issue.isError ? 'Nie udało się wczytać zadania z Jiry.'
        : <><strong>{issue.data.summary}</strong><span className="jira-tooltip-meta">{issue.data.issueType} · {issue.data.status}</span></>}
    </span>}
  </span>
}
