import { useEffect, useId, useState, type FormEvent } from 'react'
import { ArrowLeftRight, Clock3, Pause, PencilLine, Play, Square, Trash2 } from 'lucide-react'
import { useI18n, useMessages } from '../../content/i18n'
import { formatClock, formatDuration } from '../../domain/duration'
import { useUser } from '../../app/session'
import type { TimeEntry } from '../../lib/api'
import { projectColorHex } from '../../lib/projectColors'
import {
  clearTimerLocalState,
  readTimerDraft,
  saveTimerDraft,
  timerElapsedSeconds,
  useActiveTimer,
  useChangeTimerState,
  useDiscardTimer,
  useStartTimer,
  useStopTimer,
  useSwitchTimer,
  useUpdateTimer,
} from '../../lib/time'
import { Button, Dialog, Dot, Field, Notice } from '../../ui'
import { useToast } from '../../ui/toast'
import { JiraField, JiraKey, TagField, TemplateField, type JiraIssueLink } from '../entries/EntryExtras'
import type { WorkProject } from './data'
import { ProjectPicker } from './ProjectPicker'

const messages = {
  pl: {
    details: 'Więcej opcji',
    hideDetails: 'Ukryj szczegóły',
    required: 'Ten zespół wymaga tagu lub zadania Jira.',
    noteRunning: 'Opis pracy',
    noteHint: 'Zapisze się przy zatrzymaniu lub zakończeniu.',
    change: 'Zmień przypisanie',
    changeTitle: 'Zmień przypisanie bieżącej pracy',
    changeBody: 'Pomiar trwa dalej. Zmieni się projekt, tag lub zadanie tej samej pracy.',
    switch: 'Zakończ i zacznij nową',
    switchTitle: 'Zakończ i rozpocznij kolejną pracę',
    switchBody: 'Obecna praca zapisze się jako wpis, a pomiar nowej zacznie się od zera.',
    finishedNote: 'Opis kończonej pracy',
    newProject: 'Nowa praca',
    newNote: 'Opis nowej pracy',
    switching: 'Przełączanie…',
    switched: 'Zapisano {duration}. Mierzysz teraz: {project}.',
    discard: 'Odrzuć',
    discardTitle: 'Odrzucić bieżący pomiar?',
    discardBody: 'Czas {duration} nie zostanie zapisany. Tej operacji nie można cofnąć.',
    discardConfirm: 'Odrzuć bez zapisu',
    discarded: 'Pomiar odrzucony.',
    saveChange: 'Zapisz zmianę',
    saving: 'Zapisywanie…',
    lostAccess: 'Nie masz już dostępu do tego zespołu. Możesz zakończyć pomiar, ale nie możesz go wznowić.',
    paused: 'Przerwa nie liczy się do czasu pracy.',
  },
  en: {
    details: 'More options',
    hideDetails: 'Hide details',
    required: 'This team requires a tag or a Jira issue.',
    noteRunning: 'Work description',
    noteHint: 'Saved when you pause or stop.',
    change: 'Change assignment',
    changeTitle: 'Change the assignment of the current work',
    changeBody: 'Tracking continues. Only the project, tag or issue of the same work change.',
    switch: 'Stop and start new',
    switchTitle: 'Stop and start the next work',
    switchBody: 'The current work is saved as an entry and the new one starts from zero.',
    finishedNote: 'Description of the finished work',
    newProject: 'New work',
    newNote: 'Description of the new work',
    switching: 'Switching…',
    switched: 'Saved {duration}. Now tracking: {project}.',
    discard: 'Discard',
    discardTitle: 'Discard the current tracking?',
    discardBody: '{duration} will not be saved. This cannot be undone.',
    discardConfirm: 'Discard without saving',
    discarded: 'Tracking discarded.',
    saveChange: 'Save the change',
    saving: 'Saving…',
    lostAccess: 'You no longer have access to this team. You can stop tracking but not resume it.',
    paused: 'Breaks do not count as work time.',
  },
}

/** Timer card of My work: start with project, description and optional details; full control of the running timer. */
export function MeasureCard({
  projects,
  allProjects,
  recentProjectIds,
  loading,
}: {
  projects: WorkProject[]
  allProjects: WorkProject[]
  recentProjectIds: string[]
  loading: boolean
}) {
  const user = useUser()
  const { t } = useI18n()
  const active = useActiveTimer(user.id)
  const entry = active.data ?? null
  const running = entry ? (allProjects.find(item => item.id === entry.projectId) ?? null) : null
  return (
    <article className={`card measure ${entry ? 'is-running' : ''}`} aria-labelledby="measure-title">
      <div className="card-head">
        <h2 id="measure-title">
          <Clock3 size={18} aria-hidden="true" />
          {t('work.measure')}
        </h2>
        {entry && (
          <span className={`badge ${entry.pausedAt ? '' : 'badge-live'}`}>
            {t(entry.pausedAt ? 'work.paused' : 'work.running')}
          </span>
        )}
      </div>
      {entry ? (
        <RunningTimer key={entry.id} entry={entry} project={running} projects={allProjects} />
      ) : (
        <StartForm projects={projects} recentProjectIds={recentProjectIds} loading={loading} />
      )}
    </article>
  )
}

function StartForm({
  projects,
  recentProjectIds,
  loading,
}: {
  projects: WorkProject[]
  recentProjectIds: string[]
  loading: boolean
}) {
  const user = useUser()
  const { t } = useI18n()
  const m = useMessages(messages)
  const start = useStartTimer(user.id)
  const [project, setProject] = useState<WorkProject | null>(null)
  const [description, setDescription] = useState('')
  const [tagId, setTagId] = useState('')
  const [issue, setIssue] = useState<JiraIssueLink | null>(null)
  const [details, setDetails] = useState(false)
  const labelId = useId()
  const chosen = project
    ? (projects.find(item => item.id === project.id) ?? null)
    : recentProjectIds[0]
      ? (projects.find(item => item.id === recentProjectIds[0]) ?? null)
      : (projects[0] ?? null)
  const showDetails = details || !!chosen?.team.requireTagOrIssue
  const recent = recentProjectIds
    .map(id => projects.find(item => item.id === id))
    .filter((item): item is WorkProject => !!item && item.id !== chosen?.id)
    .slice(0, 3)

  const begin = (target: WorkProject | null, withDetails = true) => {
    if (!target) return
    const sameTeam = withDetails && target.teamId === chosen?.teamId
    start.mutate(
      {
        teamId: target.teamId,
        projectId: target.id,
        description: description.trim() || null,
        ...(sameTeam && tagId ? { tagId } : {}),
        ...(sameTeam && issue ? { jiraIssueId: issue.id } : {}),
      },
      {
        onSuccess: () => {
          setDescription('')
          setTagId('')
          setIssue(null)
        },
      },
    )
  }
  const pick = (next: WorkProject) => {
    if (next.teamId !== chosen?.teamId) {
      setTagId('')
      setIssue(null)
    }
    setProject(next)
  }

  return (
    <form
      onSubmit={(event: FormEvent) => {
        event.preventDefault()
        begin(chosen)
      }}
      className="stack"
    >
      <div className="field">
        <span className="field-label" id={labelId}>
          {t('work.project')}
        </span>
        <ProjectPicker projects={projects} value={chosen} onChange={pick} labelledBy={labelId} disabled={loading} />
      </div>
      <Field label={t('work.whatNow')} optional={!chosen?.team.requireDescription}>
        {props => (
          <input
            {...props}
            className="input"
            value={description}
            maxLength={2000}
            placeholder={t('work.whatNow.placeholder')}
            onChange={event => setDescription(event.target.value)}
          />
        )}
      </Field>
      {chosen &&
        (showDetails ? (
          <div className="stack details">
            {chosen.team.requireTagOrIssue && <p className="field-hint">{m('required')}</p>}
            <TagField
              userId={user.id}
              teamId={chosen.teamId}
              value={tagId}
              onChange={setTagId}
              canCreate={chosen.team.role !== 'MEMBER'}
            />
            <JiraField userId={user.id} teamId={chosen.teamId} value={issue} onChange={setIssue} />
            <TemplateField
              userId={user.id}
              teamId={chosen.teamId}
              current={{ projectId: chosen.id, description, issue, tagId }}
              onApply={template => {
                const target = projects.find(item => item.id === template.projectId)
                if (target) setProject(target)
                setDescription(template.description ?? '')
                setTagId(template.tagId && !template.tagArchived ? template.tagId : '')
                setIssue(
                  template.jiraIssueId && template.jiraIssueKey
                    ? { id: template.jiraIssueId, key: template.jiraIssueKey, summary: '' }
                    : null,
                )
              }}
            />
            {!chosen.team.requireTagOrIssue && (
              <button type="button" className="link-btn details-toggle" onClick={() => setDetails(false)}>
                {m('hideDetails')}
              </button>
            )}
          </div>
        ) : (
          <button
            type="button"
            className="link-btn details-toggle"
            aria-expanded="false"
            onClick={() => setDetails(true)}
          >
            {m('details')}
          </button>
        ))}
      {start.isError && <Notice tone="error">{start.error.message}</Notice>}
      <Button type="submit" variant="primary" wide pending={start.isPending} disabled={loading || !chosen}>
        <Play size={16} aria-hidden="true" />
        {start.isPending ? t('work.starting') : t('work.start')}
      </Button>
      {!!recent.length && (
        <div className="quick">
          <p>{t('work.recent')}</p>
          <ul>
            {recent.map(item => (
              <li key={item.id}>
                <button
                  type="button"
                  className="chip"
                  disabled={start.isPending}
                  aria-label={t('work.startProject', { project: item.name, team: item.team.name })}
                  onClick={() => begin(item, false)}
                >
                  <Dot color={projectColorHex(item.color)} />
                  {item.name}
                  <small>{item.team.name}</small>
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </form>
  )
}

function useTicking(entry: TimeEntry) {
  const [now, setNow] = useState(Date.now)
  useEffect(() => {
    if (entry.pausedAt) return
    const tick = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(tick)
  }, [entry.pausedAt])
  return timerElapsedSeconds(entry, now)
}

function RunningTimer({
  entry,
  project,
  projects,
}: {
  entry: TimeEntry
  project: WorkProject | null
  projects: WorkProject[]
}) {
  const user = useUser()
  const { t } = useI18n()
  const m = useMessages(messages)
  const toast = useToast()
  const stop = useStopTimer(user.id)
  const state = useChangeTimerState(user.id)
  const [note, setNote] = useState(() => readTimerDraft(user.id, entry.id) ?? entry.description ?? '')
  const [dialog, setDialog] = useState<'change' | 'switch' | 'discard' | null>(null)
  const elapsed = useTicking(entry)
  const description = note.trim() || null
  const lostAccess = !project
  const busy = stop.isPending || state.isPending

  return (
    <div className="stack">
      <p className="measure-project">
        <Dot color={projectColorHex(project?.color)} />
        {project?.name ?? '…'} <small>{project?.team.name}</small>
        <JiraKey userId={user.id} teamId={entry.teamId} issueKey={entry.jiraIssueKey} issueId={entry.jiraIssueId} />
      </p>
      {/* The clock is not announced every second; its label gives the elapsed time to screen readers on demand. */}
      <p className="big-digits num" role="timer" aria-live="off" aria-label={formatDuration(elapsed)}>
        {formatClock(elapsed)}
      </p>
      {entry.pausedAt && <p className="field-hint">{m('paused')}</p>}
      {lostAccess && <Notice tone="warning">{m('lostAccess')}</Notice>}
      <Field label={m('noteRunning')} hint={m('noteHint')} optional={!project?.team.requireDescription}>
        {props => (
          <textarea
            {...props}
            className="textarea input"
            rows={2}
            maxLength={2000}
            value={note}
            onChange={event => {
              setNote(event.target.value)
              saveTimerDraft(user.id, entry.id, event.target.value)
            }}
          />
        )}
      </Field>
      {(stop.error ?? state.error) && <Notice tone="error">{(stop.error ?? state.error)!.message}</Notice>}
      <div className="row">
        <Button
          onClick={() => state.mutate({ action: entry.pausedAt ? 'resume' : 'pause', entryId: entry.id, description })}
          disabled={busy || (!!entry.pausedAt && lostAccess)}
        >
          {entry.pausedAt ? <Play size={16} aria-hidden="true" /> : <Pause size={16} aria-hidden="true" />}
          {t(entry.pausedAt ? 'work.resume' : 'work.pause')}
        </Button>
        <Button
          variant="primary"
          pending={stop.isPending}
          disabled={busy || elapsed < 1}
          onClick={() =>
            void stop.mutateAsync({ entryId: entry.id, description }).then(
              () => {
                clearTimerLocalState(user.id, entry.id)
                toast(t('work.stopped', { duration: formatDuration(elapsed), project: project?.name ?? '' }))
              },
              () => undefined,
            )
          }
        >
          <Square size={15} aria-hidden="true" />
          {t('work.stop')}
        </Button>
      </div>
      <div className="inline-actions">
        <button type="button" className="link-btn" disabled={busy || lostAccess} onClick={() => setDialog('change')}>
          <PencilLine size={14} aria-hidden="true" /> {m('change')}
        </button>
        <button
          type="button"
          className="link-btn"
          disabled={busy || !!entry.pausedAt || elapsed < 1}
          onClick={() => setDialog('switch')}
        >
          <ArrowLeftRight size={14} aria-hidden="true" /> {m('switch')}
        </button>
        <button type="button" className="link-btn danger-link" disabled={busy} onClick={() => setDialog('discard')}>
          <Trash2 size={14} aria-hidden="true" /> {m('discard')}
        </button>
      </div>
      {dialog === 'change' && project && (
        <ChangeDialog
          entry={entry}
          project={project}
          projects={projects.filter(item => item.teamId === entry.teamId)}
          description={description}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog === 'switch' && (
        <SwitchDialog entry={entry} note={note} projects={projects} elapsed={elapsed} onClose={() => setDialog(null)} />
      )}
      {dialog === 'discard' && <DiscardDialog entry={entry} elapsed={elapsed} onClose={() => setDialog(null)} />}
    </div>
  )
}

function ChangeDialog({
  entry,
  project,
  projects,
  description,
  onClose,
}: {
  entry: TimeEntry
  project: WorkProject
  projects: WorkProject[]
  description: string | null
  onClose: () => void
}) {
  const user = useUser()
  const { t } = useI18n()
  const m = useMessages(messages)
  const update = useUpdateTimer(user.id)
  const [target, setTarget] = useState(project)
  const [tagId, setTagId] = useState(entry.tagId ?? '')
  const [issue, setIssue] = useState<JiraIssueLink | null>(
    entry.jiraIssueId && entry.jiraIssueKey ? { id: entry.jiraIssueId, key: entry.jiraIssueKey, summary: '' } : null,
  )
  const labelId = useId()
  return (
    <Dialog open title={m('changeTitle')} onClose={onClose} busy={update.isPending}>
      <p className="muted">{m('changeBody')}</p>
      <div className="field">
        <span className="field-label" id={labelId}>
          {t('work.project')}
        </span>
        <ProjectPicker projects={projects} value={target} onChange={setTarget} labelledBy={labelId} />
      </div>
      <TagField userId={user.id} teamId={entry.teamId} value={tagId} onChange={setTagId} />
      <JiraField userId={user.id} teamId={entry.teamId} value={issue} onChange={setIssue} />
      {update.isError && <Notice tone="error">{update.error.message}</Notice>}
      <div className="dialog-actions">
        <Button onClick={onClose} disabled={update.isPending}>
          {t('common.cancel')}
        </Button>
        <Button
          variant="primary"
          pending={update.isPending}
          onClick={() =>
            update.mutate(
              {
                entryId: entry.id,
                projectId: target.id,
                description,
                jiraIssueId: issue?.id ?? null,
                tagId: tagId || null,
              },
              { onSuccess: onClose },
            )
          }
        >
          {m(update.isPending ? 'saving' : 'saveChange')}
        </Button>
      </div>
    </Dialog>
  )
}

function SwitchDialog({
  entry,
  note,
  projects,
  elapsed,
  onClose,
}: {
  entry: TimeEntry
  note: string
  projects: WorkProject[]
  elapsed: number
  onClose: () => void
}) {
  const user = useUser()
  const { t } = useI18n()
  const m = useMessages(messages)
  const toast = useToast()
  const [finished, setFinished] = useState(note)
  const [target, setTarget] = useState<WorkProject | null>(null)
  const [next, setNext] = useState('')
  const labelId = useId()
  const switchTimer = useSwitchTimer(user.id, () => clearTimerLocalState(user.id, entry.id))
  const submit = () => {
    if (!target) return
    // The dialog disappears with the old timer, so the confirmation follows the promise, not a component callback.
    void switchTimer
      .mutateAsync({
        stop: { entryId: entry.id, description: finished.trim() || null },
        start: { teamId: target.teamId, projectId: target.id, description: next.trim() || null },
      })
      .then(
        () => toast(m('switched', { duration: formatDuration(elapsed), project: target.name })),
        () => undefined,
      )
  }
  return (
    <Dialog open title={m('switchTitle')} onClose={onClose} busy={switchTimer.isPending}>
      <p className="muted">{m('switchBody')}</p>
      <Field label={m('finishedNote')} optional>
        {props => (
          <input
            {...props}
            className="input"
            value={finished}
            maxLength={2000}
            onChange={event => setFinished(event.target.value)}
          />
        )}
      </Field>
      <div className="field">
        <span className="field-label" id={labelId}>
          {m('newProject')}
        </span>
        <ProjectPicker projects={projects} value={target} onChange={setTarget} labelledBy={labelId} />
      </div>
      <Field label={m('newNote')} optional>
        {props => (
          <input
            {...props}
            className="input"
            value={next}
            maxLength={2000}
            onChange={event => setNext(event.target.value)}
          />
        )}
      </Field>
      {switchTimer.isError && <Notice tone="error">{switchTimer.error.message}</Notice>}
      <div className="dialog-actions">
        <Button onClick={onClose} disabled={switchTimer.isPending}>
          {t('common.cancel')}
        </Button>
        <Button variant="primary" pending={switchTimer.isPending} disabled={!target} onClick={submit}>
          {m(switchTimer.isPending ? 'switching' : 'switch')}
        </Button>
      </div>
    </Dialog>
  )
}

function DiscardDialog({ entry, elapsed, onClose }: { entry: TimeEntry; elapsed: number; onClose: () => void }) {
  const user = useUser()
  const { t } = useI18n()
  const m = useMessages(messages)
  const toast = useToast()
  const discard = useDiscardTimer(user.id)
  return (
    <Dialog open title={m('discardTitle')} onClose={onClose} busy={discard.isPending}>
      <p>{m('discardBody', { duration: formatDuration(elapsed) })}</p>
      {discard.isError && <Notice tone="error">{discard.error.message}</Notice>}
      <div className="dialog-actions">
        <Button onClick={onClose} disabled={discard.isPending}>
          {t('common.cancel')}
        </Button>
        <Button
          variant="danger"
          pending={discard.isPending}
          onClick={() =>
            void discard.mutateAsync({ entryId: entry.id }).then(
              () => {
                clearTimerLocalState(user.id, entry.id)
                toast(m('discarded'))
              },
              () => undefined,
            )
          }
        >
          {m('discardConfirm')}
        </Button>
      </div>
    </Dialog>
  )
}
