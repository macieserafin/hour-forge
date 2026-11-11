import { useEffect, useId, useState } from 'react'
import { Search, X } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useI18n, useMessages } from '../../content/i18n'
import type { EntryTemplate, JiraIssueFilters } from '../../lib/api'
import { useJiraIssue, useJiraIssues, useJiraStatus } from '../../lib/jira'
import { useTags } from '../../lib/tags'
import { useCreateTemplate, useDeleteTemplate, useTemplates } from '../../lib/templates'
import { Button, Field, Notice } from '../../ui'
import './entries.css'

const messages = {
  pl: {
    tag: 'Tag',
    noTag: 'Bez tagu',
    archived: '{name} (archiwalny)',
    noTags: 'Zespół nie ma jeszcze tagów.',
    addTags: 'Dodaj tagi w zespole',
    jira: 'Zadanie Jira',
    pick: 'Wybierz zadanie',
    hide: 'Zwiń wyszukiwarkę',
    connect: 'Aby powiązać wpis z zadaniem, połącz swoje konto Jira.',
    connectLink: 'Połącz konto',
    remove: 'Usuń powiązanie z zadaniem {key}',
    search: 'Szukaj zadania',
    searchPlaceholder: 'Klucz lub fragment tytułu',
    assignee: 'Zadania',
    mine: 'Przypisane do mnie',
    anyone: 'Wszystkie',
    sprint: 'Sprint',
    anySprint: 'Dowolny',
    activeSprint: 'Aktywny sprint',
    closedSprint: 'Zakończone sprinty',
    done: 'Pokaż także ukończone',
    searching: 'Szukanie zadań…',
    none: 'Nie znaleziono zadań.',
    found: 'Znaleziono zadań: {count}',
    more: 'Pokaż więcej zadań',
    results: 'Wyniki wyszukiwania zadań Jira',
    template: 'Szablon',
    noTemplate: 'Bez szablonu',
    saveAs: 'Zapisz jako szablon',
    cancelSave: 'Anuluj',
    templateName: 'Nazwa szablonu',
    saveTemplate: 'Zapisz szablon',
    deleteTemplate: 'Usuń szablon „{name}”',
    needName: 'Wpisz nazwę szablonu.',
    needProject: 'Najpierw wybierz projekt, który ma być w szablonie.',
    issueLoading: 'Wczytywanie zadania…',
    issueFailed: 'Nie udało się wczytać zadania z Jiry.',
    issueOpens: 'Zadanie Jira {key} (otwiera się w nowej karcie)',
  },
  en: {
    tag: 'Tag',
    noTag: 'No tag',
    archived: '{name} (archived)',
    noTags: 'The team has no tags yet.',
    addTags: 'Add tags in the team',
    jira: 'Jira issue',
    pick: 'Choose an issue',
    hide: 'Close the search',
    connect: 'To link an entry to an issue, connect your Jira account.',
    connectLink: 'Connect account',
    remove: 'Remove the link to {key}',
    search: 'Search issues',
    searchPlaceholder: 'Key or part of the title',
    assignee: 'Issues',
    mine: 'Assigned to me',
    anyone: 'All',
    sprint: 'Sprint',
    anySprint: 'Any',
    activeSprint: 'Active sprint',
    closedSprint: 'Closed sprints',
    done: 'Include done issues',
    searching: 'Searching issues…',
    none: 'No issues found.',
    found: 'Issues found: {count}',
    more: 'Show more issues',
    results: 'Jira issue search results',
    template: 'Template',
    noTemplate: 'No template',
    saveAs: 'Save as a template',
    cancelSave: 'Cancel',
    templateName: 'Template name',
    saveTemplate: 'Save template',
    deleteTemplate: 'Delete template "{name}"',
    needName: 'Enter a template name.',
    needProject: 'Choose the project the template should use first.',
    issueLoading: 'Loading the issue…',
    issueFailed: 'The issue could not be loaded from Jira.',
    issueOpens: 'Jira issue {key} (opens in a new tab)',
  },
}

export interface JiraIssueLink {
  id: string
  key: string
  summary: string
}

/** Work tag of an entry. Hidden while the team has no tags; an archived tag the entry already has stays selectable. */
export function TagField({
  userId,
  teamId,
  value,
  current,
  onChange,
  disabled,
  canCreate,
}: {
  userId: string
  teamId: string
  value: string
  current?: { id: string; name: string } | null
  onChange: (tagId: string) => void
  disabled?: boolean
  canCreate?: boolean
}) {
  const m = useMessages(messages)
  const tags = useTags(userId, teamId)
  const options = tags.data ?? []
  const kept = current && !options.some(tag => tag.id === current.id) ? current : null
  if (!options.length && !kept)
    return canCreate && tags.isSuccess ? (
      <p className="field-hint">
        {m('noTags')}{' '}
        <Link className="text-link" to={`/app/teams/${encodeURIComponent(teamId)}/tags`}>
          {m('addTags')}
        </Link>
      </p>
    ) : null
  return (
    <Field label={m('tag')} optional>
      {props => (
        <select
          {...props}
          className="select input"
          value={value}
          disabled={disabled}
          onChange={event => onChange(event.target.value)}
        >
          <option value="">{m('noTag')}</option>
          {kept && <option value={kept.id}>{m('archived', { name: kept.name })}</option>}
          {options.map(tag => (
            <option key={tag.id} value={tag.id}>
              {tag.name}
            </option>
          ))}
        </select>
      )}
    </Field>
  )
}

/** Optional Jira issue. Nothing shows without Jira in the team; without a personal connection, a link explains how to add it. */
export function JiraField({
  userId,
  teamId,
  value,
  onChange,
  disabled,
}: {
  userId: string
  teamId: string
  value: JiraIssueLink | null
  onChange: (issue: JiraIssueLink | null) => void
  disabled?: boolean
}) {
  const m = useMessages(messages)
  const { t } = useI18n()
  const status = useJiraStatus(userId, teamId)
  const [open, setOpen] = useState(false)
  const labelId = useId()
  if (!status.data?.available || !status.data.siteConnected) return null
  if (!status.data.userConnected)
    return (
      <p className="field-hint">
        {m('connect')}{' '}
        <Link className="text-link" to="/app/account/integrations">
          {m('connectLink')}
        </Link>
      </p>
    )
  return (
    <div className="field">
      <span className="field-label" id={labelId}>
        {m('jira')} <i>{t('common.optional')}</i>
      </span>
      {value ? (
        <div className="jira-chosen" role="group" aria-labelledby={labelId}>
          <b>{value.key}</b>
          <span>{value.summary}</span>
          <button
            type="button"
            className="icon-btn"
            disabled={disabled}
            aria-label={m('remove', { key: value.key })}
            onClick={() => onChange(null)}
          >
            <X size={16} />
          </button>
        </div>
      ) : (
        <>
          <Button small disabled={disabled} aria-expanded={open} onClick={() => setOpen(!open)}>
            {m(open ? 'hide' : 'pick')}
          </Button>
          {open && (
            <IssueSearch
              userId={userId}
              teamId={teamId}
              onPick={issue => {
                onChange(issue)
                setOpen(false)
              }}
            />
          )}
        </>
      )}
    </div>
  )
}

function IssueSearch({
  userId,
  teamId,
  onPick,
}: {
  userId: string
  teamId: string
  onPick: (issue: JiraIssueLink) => void
}) {
  const m = useMessages(messages)
  const { t } = useI18n()
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
  return (
    <div className="jira-search">
      <Field label={m('search')}>
        {props => (
          <div className="input-wrap">
            <Search size={16} aria-hidden="true" />
            <input
              {...props}
              className="input"
              type="search"
              maxLength={100}
              placeholder={m('searchPlaceholder')}
              value={text}
              onChange={event => setText(event.target.value)}
            />
          </div>
        )}
      </Field>
      <div className="jira-filters">
        <Field label={m('assignee')}>
          {props => (
            <select
              {...props}
              className="select input"
              value={assignee}
              onChange={event => setAssignee(event.target.value as typeof assignee)}
            >
              <option value="ME">{m('mine')}</option>
              <option value="ANYONE">{m('anyone')}</option>
            </select>
          )}
        </Field>
        <Field label={m('sprint')}>
          {props => (
            <select
              {...props}
              className="select input"
              value={sprint}
              onChange={event => setSprint(event.target.value as typeof sprint)}
            >
              <option value="ANY">{m('anySprint')}</option>
              <option value="ACTIVE">{m('activeSprint')}</option>
              <option value="CLOSED">{m('closedSprint')}</option>
            </select>
          )}
        </Field>
      </div>
      <label className="check">
        <input type="checkbox" checked={includeDone} onChange={event => setIncludeDone(event.target.checked)} />
        {m('done')}
      </label>
      <p className="field-hint" role="status">
        {issues.isPending
          ? m('searching')
          : issues.isError
            ? ''
            : list.length
              ? m('found', { count: `${list.length}${issues.hasNextPage ? '+' : ''}` })
              : m('none')}
      </p>
      {issues.isError && (
        <Notice
          tone="error"
          action={
            <div>
              <Button small onClick={() => void issues.refetch()}>
                {t('common.retry')}
              </Button>
            </div>
          }
        >
          {issues.error.message}
        </Notice>
      )}
      {!!list.length && (
        <ul className="jira-results" aria-label={m('results')}>
          {list.map(issue => (
            <li key={issue.id}>
              <button type="button" onClick={() => onPick({ id: issue.id, key: issue.key, summary: issue.summary })}>
                <b>{issue.key}</b> <span>{issue.summary}</span>{' '}
                <small>
                  {issue.issueType} · {issue.status}
                </small>
              </button>
            </li>
          ))}
        </ul>
      )}
      {issues.hasNextPage && (
        <Button small pending={issues.isFetchingNextPage} onClick={() => void issues.fetchNextPage()}>
          {m('more')}
        </Button>
      )}
    </div>
  )
}

/** Personal starting points in one team: choose one to fill the form or save the current form as a new one. */
export function TemplateField({
  userId,
  teamId,
  current,
  onApply,
}: {
  userId: string
  teamId: string
  current: { projectId: string; description: string; issue: JiraIssueLink | null; tagId: string }
  onApply: (template: EntryTemplate) => void
}) {
  const m = useMessages(messages)
  const templates = useTemplates(userId, teamId)
  const create = useCreateTemplate(userId, teamId)
  const remove = useDeleteTemplate(userId, teamId)
  const [selected, setSelected] = useState('')
  const [saving, setSaving] = useState(false)
  const [name, setName] = useState('')
  const [validation, setValidation] = useState('')
  const list = templates.data ?? []
  const chosen = list.find(template => template.id === selected)
  if (templates.isError) return null
  const save = () => {
    if (!name.trim()) {
      setValidation(m('needName'))
      return
    }
    if (!current.projectId) {
      setValidation(m('needProject'))
      return
    }
    setValidation('')
    create.mutate(
      {
        name: name.trim(),
        projectId: current.projectId,
        description: current.description.trim() || null,
        jiraIssueId: current.issue?.id ?? null,
        tagId: current.tagId || null,
      },
      {
        onSuccess: created => {
          setSelected(created.id)
          setSaving(false)
          setName('')
        },
      },
    )
  }
  return (
    <div className="stack-sm">
      {!!list.length && (
        <Field label={m('template')} optional>
          {props => (
            <select
              {...props}
              className="select input"
              value={selected}
              disabled={templates.isPending}
              onChange={event => {
                setSelected(event.target.value)
                const found = list.find(item => item.id === event.target.value)
                if (found) onApply(found)
              }}
            >
              <option value="">{m('noTemplate')}</option>
              {list.map(template => (
                <option key={template.id} value={template.id}>
                  {template.name}
                </option>
              ))}
            </select>
          )}
        </Field>
      )}
      <div className="inline-actions">
        <button
          type="button"
          className="link-btn"
          onClick={() => {
            setSaving(!saving)
            setValidation('')
          }}
        >
          {m(saving ? 'cancelSave' : 'saveAs')}
        </button>
        {chosen && (
          <button
            type="button"
            className="link-btn"
            disabled={remove.isPending}
            onClick={() => remove.mutate(chosen.id, { onSuccess: () => setSelected('') })}
          >
            {m('deleteTemplate', { name: chosen.name })}
          </button>
        )}
      </div>
      {saving && (
        <div className="inline-form">
          <Field label={m('templateName')}>
            {props => (
              <input
                {...props}
                className="input"
                maxLength={80}
                value={name}
                onChange={event => {
                  setName(event.target.value)
                  setValidation('')
                }}
                onKeyDown={event => {
                  if (event.key === 'Enter') {
                    event.preventDefault()
                    save()
                  }
                }}
              />
            )}
          </Field>
          <Button small pending={create.isPending} onClick={save}>
            {m('saveTemplate')}
          </Button>
        </div>
      )}
      {(validation || create.isError || remove.isError) && (
        <p className="field-error" role="alert">
          {validation || create.error?.message || remove.error?.message}
        </p>
      )}
    </div>
  )
}

const ISSUE_KEY = /^[A-Za-z][A-Za-z0-9_]{1,19}-[0-9]{1,9}$/

/** Issue key of a linked entry; links to Jira when the site address is safe and shows the live title on hover or focus. */
export function JiraKey({
  userId,
  teamId,
  issueKey,
  issueId,
}: {
  userId: string
  teamId: string
  issueKey: string | null | undefined
  issueId?: string | null
}) {
  const m = useMessages(messages)
  const status = useJiraStatus(userId, teamId)
  const [open, setOpen] = useState(false)
  const tooltipId = useId()
  const canDescribe = !!issueId && status.data?.userConnected === true
  const issue = useJiraIssue(userId, teamId, issueId ?? '', open && canDescribe)
  if (!issueKey) return null
  const site = status.data?.siteUrl
  const handlers = canDescribe
    ? {
        onMouseEnter: () => setOpen(true),
        onMouseLeave: () => setOpen(false),
        onFocus: () => setOpen(true),
        onBlur: () => setOpen(false),
        onKeyDown: (event: React.KeyboardEvent) => {
          if (event.key === 'Escape') setOpen(false)
        },
        'aria-describedby': open ? tooltipId : undefined,
      }
    : {}
  const key =
    site?.startsWith('https://') && ISSUE_KEY.test(issueKey) ? (
      <a
        className="jira-key"
        href={`${site.replace(/\/+$/, '')}/browse/${issueKey}`}
        target="_blank"
        rel="noopener noreferrer"
        aria-label={m('issueOpens', { key: issueKey })}
        {...handlers}
      >
        {issueKey}
      </a>
    ) : (
      <span className="jira-key" tabIndex={canDescribe ? 0 : undefined} {...handlers}>
        {issueKey}
      </span>
    )
  if (!canDescribe) return key
  return (
    <span className="jira-key-wrap">
      {key}
      {open && (
        <span role="tooltip" id={tooltipId} className="jira-tooltip">
          {issue.isPending ? (
            m('issueLoading')
          ) : issue.isError ? (
            m('issueFailed')
          ) : (
            <>
              <b>{issue.data.summary}</b>
              <small>
                {issue.data.issueType} · {issue.data.status}
              </small>
            </>
          )}
        </span>
      )}
    </span>
  )
}
