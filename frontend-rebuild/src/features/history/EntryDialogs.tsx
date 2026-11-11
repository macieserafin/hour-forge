import { useId, useRef, useState, type FormEvent } from 'react'
import { useI18n, useMessages } from '../../content/i18n'
import { formatDuration, formatWorkDate, MAX_ENTRY_SECONDS, parseDuration } from '../../domain/duration'
import { useUser } from '../../app/session'
import type { TimeEntryHistoryItem } from '../../lib/api'
import { addDays, newRequestId, teamToday, useCopyDay, useDeleteTimeEntry, useManualTimeEntry, useTimeEntryHistory, useUpdateTimeEntry, workDateRange } from '../../lib/time'
import { Button, Dialog, Field, Loading, Notice } from '../../ui'
import { useToast } from '../../ui/toast'
import { JiraField, TagField, type JiraIssueLink } from '../entries/EntryExtras'
import type { WorkProject } from '../work/data'
import { ProjectPicker } from '../work/ProjectPicker'

const messages = {
  pl: {
    edit: 'Edytuj wpis', add: 'Dodaj czas', undated: 'Bez daty (nie pamiętam)', timerDate: 'Wpis z timera musi mieć datę.', correction: 'Korekta czasu nie zmienia zapisanych momentów startu i końca timera.',
    save: 'Zapisz zmiany', saving: 'Zapisywanie…', saved: 'Zapisano zmiany.', added: 'Dodano {duration}.', needDate: 'Wybierz datę albo zaznacz „Bez daty”.',
    deleteTitle: 'Usunąć wpis?', deleteBody: '{project}, {duration}, {when}. Wpis zniknie z historii i raportów. Tej operacji nie można cofnąć.', delete: 'Usuń wpis', deleting: 'Usuwanie…', deleted: 'Wpis usunięty.',
    repeat: 'Powtórz wczorajszy dzień', repeatBody: 'Skopiujemy wpisy z {from} na {to} jako wpisy ręczne (projekt, opis, czas, tag i zadanie Jira).', repeatEmpty: 'Wczoraj ({from}) nie masz wpisów w zespole {team}.',
    repeatMore: 'To początek listy; skopiujemy wszystkie wpisy z tego dnia.', repeatTotal: 'Łącznie: {duration}.', repeatDup: 'Jeśli na ten dzień masz już takie wpisy, powstaną kopie.',
    repeatDo: 'Skopiuj wpisy', repeatDoing: 'Kopiowanie…', repeatDone: 'Skopiowano wpisów: {count}, łącznie {duration}.', loading: 'Wczytywanie wpisów z wczoraj…',
  },
  en: {
    edit: 'Edit entry', add: 'Add time', undated: "No date (I don't remember)", timerDate: 'A timer entry must have a date.', correction: 'Correcting the time does not change the recorded start and end of the timer.',
    save: 'Save changes', saving: 'Saving…', saved: 'Changes saved.', added: 'Added {duration}.', needDate: 'Choose a date or tick "No date".',
    deleteTitle: 'Delete the entry?', deleteBody: '{project}, {duration}, {when}. The entry disappears from history and reports. This cannot be undone.', delete: 'Delete entry', deleting: 'Deleting…', deleted: 'Entry deleted.',
    repeat: "Repeat yesterday", repeatBody: 'We copy the entries of {from} to {to} as manual entries (project, description, time, tag and Jira issue).', repeatEmpty: 'Yesterday ({from}) you have no entries in {team}.',
    repeatMore: 'This is the start of the list; all entries of that day are copied.', repeatTotal: 'Total: {duration}.', repeatDup: 'If that day already has such entries, copies are created.',
    repeatDo: 'Copy entries', repeatDoing: 'Copying…', repeatDone: 'Copied entries: {count}, {duration} in total.', loading: "Loading yesterday's entries…",
  },
}

const hoursText = (seconds: number) => {
  const h = Math.floor(seconds / 3600), m = Math.round((seconds % 3600) / 60)
  return m ? `${h}h ${m}m` : `${h}h`
}

/**
 * U10 edit of an own entry, or a new manual entry with project and day given (week cell, "add again").
 * The version from the history is sent back, so an entry changed elsewhere is not silently overwritten (409).
 */
export function EntryDialog({ entry, preset, projects, onClose }: {
  entry?: TimeEntryHistoryItem; preset?: { project: WorkProject; workDate: string | null; description?: string | null; seconds?: number }
  projects: WorkProject[]; onClose: () => void
}) {
  const user = useUser()
  const { t } = useI18n()
  const m = useMessages(messages)
  const toast = useToast()
  const update = useUpdateTimeEntry(user.id)
  const create = useManualTimeEntry(user.id)
  const startProject = entry ? projects.find(item => item.id === entry.projectId) ?? null : preset?.project ?? null
  const team = startProject?.team
  const [project, setProject] = useState<WorkProject | null>(startProject)
  const [undated, setUndated] = useState(entry ? entry.workDate === null : preset?.workDate === null)
  const [date, setDate] = useState(entry?.workDate ?? preset?.workDate ?? (team ? teamToday(team.timezone) : ''))
  const [duration, setDuration] = useState(entry ? hoursText(entry.durationSeconds) : preset?.seconds ? hoursText(preset.seconds) : '')
  const [description, setDescription] = useState(entry?.description ?? preset?.description ?? '')
  const [tagId, setTagId] = useState(entry?.tagId ?? '')
  const [issue, setIssue] = useState<JiraIssueLink | null>(entry?.jiraIssueId && entry.jiraIssueKey ? { id: entry.jiraIssueId, key: entry.jiraIssueKey, summary: '' } : null)
  const [touched, setTouched] = useState(false)
  const labelId = useId()
  const durationRef = useRef<HTMLInputElement>(null)
  const seconds = parseDuration(duration)
  const tooLong = seconds !== null && seconds > MAX_ENTRY_SECONDS
  const isTimer = entry?.entryType === 'TIMER'
  const range = workDateRange(team?.timezone ?? 'Europe/Warsaw')
  const busy = update.isPending || create.isPending
  const error = update.error ?? create.error

  const submit = (event: FormEvent) => {
    event.preventDefault()
    setTouched(true)
    if (!project || !seconds || tooLong) { durationRef.current?.focus(); return }
    if (!undated && !date) return
    const body = { projectId: project.id, workDate: undated && !isTimer ? null : date, durationSeconds: seconds, description: description.trim() || null, tagId: tagId || null, jiraIssueId: issue?.id ?? null }
    if (entry) update.mutate({ id: entry.id, ...body, expectedVersion: entry.version }, { onSuccess: () => { toast(m('saved')); onClose() } })
    else create.mutate({ teamId: project.teamId, ...body }, { onSuccess: () => { toast(m('added', { duration: formatDuration(seconds) })); onClose() } })
  }

  return <Dialog open title={m(entry ? 'edit' : 'add')} onClose={onClose} busy={busy}>
    <form className="stack" onSubmit={submit} noValidate>
      <div className="field"><span className="field-label" id={labelId}>{t('work.project')}</span>
        <ProjectPicker projects={projects.filter(item => !team || item.teamId === team.id)} value={project} onChange={setProject} labelledBy={labelId} /></div>
      <Field label={t('work.howLong')} ok={seconds && !tooLong ? t('work.howLong.ok', { duration: formatDuration(seconds) }) : undefined}
        error={touched && !seconds ? t('work.howLong.invalid') : tooLong ? t('work.howLong.tooLong') : undefined}>
        {props => <input {...props} ref={durationRef} className="input" value={duration} autoComplete="off" onChange={event => setDuration(event.target.value)} />}</Field>
      {isTimer && <p className="field-hint">{m('correction')}</p>}
      {!isTimer && <label className="check"><input type="checkbox" checked={undated} onChange={event => setUndated(event.target.checked)} />{m('undated')}</label>}
      {(!undated || isTimer) && <Field label={t('work.when.date')} hint={isTimer ? m('timerDate') : undefined} error={touched && !date ? m('needDate') : undefined}>
        {props => <input {...props} type="date" className="input" value={date} min={range.min} max={range.max} onChange={event => setDate(event.target.value)} />}</Field>}
      <Field label={t('work.description')} optional={!team?.requireDescription}>
        {props => <textarea {...props} className="textarea input" rows={2} maxLength={2000} value={description} onChange={event => setDescription(event.target.value)} />}</Field>
      {project && <>
        <TagField userId={user.id} teamId={project.teamId} value={tagId} current={entry?.tagId && entry.tagName ? { id: entry.tagId, name: entry.tagName } : null} onChange={setTagId} />
        <JiraField userId={user.id} teamId={project.teamId} value={issue} onChange={setIssue} /></>}
      {error && <Notice tone="error">{error.message}</Notice>}
      <div className="dialog-actions"><Button onClick={onClose} disabled={busy}>{t('common.cancel')}</Button>
        <Button type="submit" variant="primary" pending={busy}>{m(busy ? 'saving' : entry ? 'save' : 'add')}</Button></div>
    </form>
  </Dialog>
}

export function DeleteEntryDialog({ entry, onClose }: { entry: TimeEntryHistoryItem; onClose: () => void }) {
  const user = useUser()
  const { t, locale } = useI18n()
  const m = useMessages(messages)
  const toast = useToast()
  const remove = useDeleteTimeEntry(user.id)
  return <Dialog open title={m('deleteTitle')} onClose={onClose} busy={remove.isPending}>
    <p>{m('deleteBody', { project: entry.projectName, duration: formatDuration(entry.durationSeconds), when: entry.workDate ? formatWorkDate(entry.workDate, locale, true) : t('work.undated') })}</p>
    {remove.isError && <Notice tone="error">{remove.error.message}</Notice>}
    <div className="dialog-actions"><Button onClick={onClose} disabled={remove.isPending}>{t('common.cancel')}</Button>
      <Button variant="danger" pending={remove.isPending} onClick={() => remove.mutate(entry.id, { onSuccess: () => { toast(m('deleted')); onClose() } })}>{m(remove.isPending ? 'deleting' : 'delete')}</Button></div>
  </Dialog>
}

/** "Repeat yesterday": copies the person's own entries of yesterday to today in one team, safely repeatable after a lost answer. */
export function RepeatDayDialog({ teamId, teamName, timezone, onClose }: { teamId: string; teamName: string; timezone: string; onClose: () => void }) {
  const user = useUser()
  const { t, locale } = useI18n()
  const m = useMessages(messages)
  const today = teamToday(timezone)
  const yesterday = addDays(today, -1)
  const copy = useCopyDay(user.id)
  // One id per opening: a retry after a lost answer is the same request, a new opening is a new copy.
  const requestId = useRef(newRequestId())
  const source = useTimeEntryHistory(user.id, teamId, { projectId: '', from: yesterday, to: yesterday }, 0)
  const items = source.data?.content ?? []
  const more = (source.data?.totalPages ?? 0) > 1
  const done = copy.data
  return <Dialog open title={m('repeat')} onClose={onClose} busy={copy.isPending}>
    {done ? <Notice tone="success">{m('repeatDone', { count: done.copied, duration: formatDuration(done.totalSeconds) })}</Notice>
      : source.isPending ? <Loading label={m('loading')} />
      : source.isError ? <Notice tone="error">{source.error.message}</Notice>
      : !items.length ? <p className="muted">{m('repeatEmpty', { from: formatWorkDate(yesterday, locale, true), team: teamName })}</p>
      : <>
        <p>{m('repeatBody', { from: formatWorkDate(yesterday, locale, true), to: formatWorkDate(today, locale, true) })}</p>
        <ul className="plain-list">{items.map(item => <li key={item.id}><b>{item.projectName}</b> · {formatDuration(item.durationSeconds)}{item.description ? ` · ${item.description}` : ''}</li>)}</ul>
        <p className="field-hint">{more ? m('repeatMore') : m('repeatTotal', { duration: formatDuration(items.reduce((sum, item) => sum + item.durationSeconds, 0)) })} {m('repeatDup')}</p>
      </>}
    {copy.isError && <Notice tone="error">{copy.error.message}</Notice>}
    <div className="dialog-actions"><Button onClick={onClose} disabled={copy.isPending}>{t(done ? 'common.close' : 'common.cancel')}</Button>
      {!done && !!items.length && <Button variant="primary" pending={copy.isPending} onClick={() => copy.mutate({ teamId, fromDate: yesterday, toDate: today, requestId: requestId.current })}>{m(copy.isPending ? 'repeatDoing' : 'repeatDo')}</Button>}</div>
  </Dialog>
}
