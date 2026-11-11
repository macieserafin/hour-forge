import { useId, useRef, useState, type FormEvent } from 'react'
import { CalendarDays, Check, Plus } from 'lucide-react'
import { useI18n, useMessages } from '../../content/i18n'
import { formatDuration, formatWorkDate, MAX_ENTRY_SECONDS, parseDuration } from '../../domain/duration'
import { useUser } from '../../app/session'
import { addDays, teamToday, useManualTimeEntry, workDateRange } from '../../lib/time'
import { Button, Field, Notice } from '../../ui'
import { useToast } from '../../ui/toast'
import { JiraField, TagField, type JiraIssueLink } from '../entries/EntryExtras'
import type { WorkProject } from './data'
import { ProjectPicker } from './ProjectPicker'

const messages = {
  pl: { details: 'Więcej opcji', needDuration: 'Wpisz czas trwania.', hideDetails: 'Ukryj szczegóły', required: 'Ten zespół wymaga tagu lub zadania Jira.', needDate: 'Wybierz datę pracy.', next: 'Dodaj kolejny wpis tego dnia' },
  en: { details: 'More options', needDuration: 'Enter a duration.', hideDetails: 'Hide details', required: 'This team requires a tag or a Jira issue.', needDate: 'Choose the work date.', next: 'Add another entry for this day' },
}

type When = 'today' | 'yesterday' | 'other' | 'unknown'

/** U08/F04: add work done earlier, also without a known date; does not touch a running timer. */
export function AddTimeCard({ projects, loading, defaultProjectId, onSaved }: { projects: WorkProject[]; loading: boolean; defaultProjectId?: string; onSaved?: (teamId: string, projectId: string, workDate: string | null, id: string) => void }) {
  const user = useUser()
  const { t, locale } = useI18n()
  const m = useMessages(messages)
  const toast = useToast()
  const save = useManualTimeEntry(user.id)
  const [duration, setDuration] = useState('')
  const [project, setProject] = useState<WorkProject | null>(null)
  const [when, setWhen] = useState<When>('today')
  const [date, setDate] = useState('')
  const [description, setDescription] = useState('')
  const [tagId, setTagId] = useState('')
  const [issue, setIssue] = useState<JiraIssueLink | null>(null)
  const [details, setDetails] = useState(false)
  const [touched, setTouched] = useState(false)
  const labelId = useId()
  const durationInput = useRef<HTMLInputElement>(null)
  const chosen = project ? projects.find(item => item.id === project.id) ?? null : defaultProjectId ? projects.find(item => item.id === defaultProjectId) ?? null : projects[0] ?? null
  const seconds = parseDuration(duration)
  const tooLong = seconds !== null && seconds > MAX_ENTRY_SECONDS
  const zone = chosen?.team.timezone ?? 'Europe/Warsaw'
  const range = workDateRange(zone)
  const workDate = when === 'unknown' ? null : when === 'today' ? teamToday(zone) : when === 'yesterday' ? addDays(teamToday(zone), -1) : date || null
  const durationError = tooLong ? t('work.howLong.tooLong') : touched && !seconds ? (duration.trim() ? t('work.howLong.invalid') : m('needDuration')) : undefined
  const ready = !!chosen && !!seconds && !tooLong && (when !== 'other' || !!date)
  const showDetails = details || !!chosen?.team.requireTagOrIssue

  const submit = (event: FormEvent) => {
    event.preventDefault()
    setTouched(true)
    if (!ready || !chosen || !seconds) { if (!seconds || tooLong) durationInput.current?.focus(); return }
    save.mutate({ teamId: chosen.teamId, projectId: chosen.id, workDate, durationSeconds: seconds, description: description.trim() || null,
      ...(tagId ? { tagId } : {}), ...(issue ? { jiraIssueId: issue.id } : {}) }, {
      onSuccess: entry => {
        onSaved?.(chosen.teamId, chosen.id, workDate, entry.id)
        toast(workDate ? t('work.saved', { duration: formatDuration(seconds), when: formatWorkDate(workDate, locale, true) }) : t('work.savedUndated', { duration: formatDuration(seconds) }))
        // The project and day stay, so several entries of one day are added one after another.
        setDuration(''); setDescription(''); setIssue(null); setTouched(false); save.reset(); durationInput.current?.focus()
      },
    })
  }

  return <article className="card add" aria-labelledby="add-title">
    <div className="card-head"><h2 id="add-title"><Plus size={18} aria-hidden="true" />{t('work.add')}</h2></div>
    <form onSubmit={submit} className="stack" noValidate>
      <div className="add-grid">
        <Field label={t('work.howLong')} hint={!duration ? t('work.howLong.hint') : undefined}
          ok={seconds && !tooLong ? t('work.howLong.ok', { duration: formatDuration(seconds) }) : undefined} error={durationError}>
          {props => <input {...props} ref={durationInput} className="input" value={duration} inputMode="text" autoComplete="off" placeholder="1h 30m"
            onChange={event => setDuration(event.target.value)} onBlur={() => { if (duration) setTouched(true) }} />}
        </Field>
        <div className="field"><span className="field-label" id={labelId}>{t('work.saveProject')}</span>
          <ProjectPicker projects={projects} value={chosen} onChange={next => { if (next.teamId !== chosen?.teamId) { setTagId(''); setIssue(null) } setProject(next) }} labelledBy={labelId} disabled={loading} /></div>
      </div>
      <fieldset className="field"><legend className="field-label">{t('work.when')}</legend>
        <div className="chips">{(['today', 'yesterday', 'other', 'unknown'] as const).map(choice => <button key={choice} type="button" className="chip" aria-pressed={when === choice}
          onClick={() => setWhen(choice)}>{choice === 'other' && <CalendarDays size={14} aria-hidden="true" />}
          {t(choice === 'today' ? 'common.today' : choice === 'yesterday' ? 'common.yesterday' : choice === 'other' ? 'work.when.other' : 'work.when.unknown')}</button>)}</div>
      </fieldset>
      {when === 'other' && <Field label={t('work.when.date')} error={touched && !date ? m('needDate') : undefined}>
        {props => <input {...props} type="date" className="input" value={date} min={range.min} max={range.max} onChange={event => setDate(event.target.value)} />}
      </Field>}
      <Field label={t('work.description')} optional={!chosen?.team.requireDescription}>
        {props => <input {...props} className="input" value={description} maxLength={2000} placeholder={t('work.description.placeholder')} onChange={event => setDescription(event.target.value)} />}
      </Field>
      {chosen && (showDetails ? <div className="stack details">
        {chosen.team.requireTagOrIssue && <p className="field-hint">{m('required')}</p>}
        <TagField userId={user.id} teamId={chosen.teamId} value={tagId} onChange={setTagId} canCreate={chosen.team.role !== 'MEMBER'} />
        <JiraField userId={user.id} teamId={chosen.teamId} value={issue} onChange={setIssue} />
        {!chosen.team.requireTagOrIssue && <button type="button" className="link-btn details-toggle" onClick={() => setDetails(false)}>{m('hideDetails')}</button>}
      </div> : <button type="button" className="link-btn details-toggle" aria-expanded="false" onClick={() => setDetails(true)}>{m('details')}</button>)}
      {save.isError && <Notice tone="error">{save.error.message}</Notice>}
      <Button type="submit" variant="accent" wide pending={save.isPending} disabled={loading || !chosen}>
        <Check size={16} aria-hidden="true" />{seconds && !tooLong ? t('work.saveTime', { duration: formatDuration(seconds) }) : t('work.saveTimeEmpty')}</Button>
    </form>
  </article>
}
