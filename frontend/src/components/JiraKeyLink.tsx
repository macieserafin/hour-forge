import { useJiraStatus } from '../lib/jira'

const ISSUE_KEY = /^[A-Za-z][A-Za-z0-9_]{1,19}-[0-9]{1,9}$/

/** The issue key of a linked entry; a link to the issue when the team's Jira address is known and looks safe. */
export function JiraKeyLink({ userId, teamId, issueKey }: { userId: string; teamId: string; issueKey: string | null | undefined }) {
  const status = useJiraStatus(userId, teamId)
  if (!issueKey) return null
  const site = status.data?.siteUrl
  if (site && site.startsWith('https://') && ISSUE_KEY.test(issueKey)) {
    return <a className="jira-key" href={`${site.replace(/\/+$/, '')}/browse/${issueKey}`} target="_blank" rel="noopener noreferrer"
      aria-label={`Zadanie Jira ${issueKey} (otwiera się w nowej karcie)`}>{issueKey}</a>
  }
  return <span className="jira-key">{issueKey}</span>
}
