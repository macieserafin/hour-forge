import { useState, type FormEvent } from 'react'
import { ChevronLeft, ChevronRight, LoaderCircle, Pencil, Plus, Trash2 } from 'lucide-react'
import { Modal } from '../components/Modal'
import { ProjectDot } from '../components/ProjectDot'
import type { Team, TeamMember, TimeEntryHistoryItem } from '../lib/api'
import { useAddMemberEntry, useCorrectEntry, useDeleteMemberEntry, useMemberTimeEntries } from '../lib/corrections'
import { useProjects } from '../lib/projects'
import { MAX_MANUAL_DURATION_SECONDS, formatDuration, maxDurationMessage, workDateRange, workDateRangeMessage } from '../lib/time'

type View = { type: 'list' } | { type: 'form'; entry: TimeEntryHistoryItem | null } | { type: 'delete'; entry: TimeEntryHistoryItem }

function CorrectionForm({ userId, team, member, entry, onDone }: { userId: string; team: Team; member: TeamMember; entry: TimeEntryHistoryItem | null; onDone: () => void }) {
  const projects = useProjects(userId, team.id, true)
  const add = useAddMemberEntry(userId, team.id, member.userId)
  const change = useCorrectEntry(userId, team.id, member.userId)
  const save = entry ? change : add
  const [projectId, setProjectId] = useState(entry?.projectId ?? '')
  const [undated, setUndated] = useState(entry ? entry.workDate === null : false)
  const [date, setDate] = useState(entry?.workDate ?? '')
  const [hours, setHours] = useState(String(entry ? Math.floor(entry.durationSeconds / 3600) : 0))
  const [minutes, setMinutes] = useState(String(entry ? Math.floor(entry.durationSeconds / 60) % 60 : 0))
  const [seconds, setSeconds] = useState(String(entry ? entry.durationSeconds % 60 : 0))
  const [description, setDescription] = useState(entry?.description ?? '')
  const [validation, setValidation] = useState('')
  const range = workDateRange(team.timezone)
  const options = (projects.data ?? []).filter(project => !project.archivedAt || project.id === entry?.projectId)

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const parts = [hours, minutes, seconds].map(value => value === '' ? 0 : Number(value))
    const duration = parts[0]! * 3600 + parts[1]! * 60 + parts[2]!
    if ((!undated && !date) || !options.some(project => project.id === projectId)) { setValidation('Wybierz projekt i datę pracy albo opcję „Bez daty”.'); return }
    if (!undated && date !== entry?.workDate && (date < range.min || date > range.max)) { setValidation(workDateRangeMessage); return }
    if (parts.some(value => !Number.isInteger(value) || value < 0) || parts[1]! > 59 || parts[2]! > 59 || duration < 1) {
      setValidation('Podaj dodatni czas w pełnych sekundach. Minuty i sekundy muszą mieścić się w zakresie 0–59.'); return
    }
    if (duration !== entry?.durationSeconds && duration > MAX_MANUAL_DURATION_SECONDS) { setValidation(maxDurationMessage); return }
    setValidation('')
    const body = { teamId: team.id, projectId, workDate: undated ? null : date, durationSeconds: duration, description: description.trim() || null }
    if (entry) change.mutate({ ...body, id: entry.id }, { onSuccess: onDone })
    else add.mutate({ ...body, memberId: member.userId }, { onSuccess: onDone })
  }
  return <form onSubmit={submit} onInput={() => setValidation('')}>
    <fieldset disabled={save.isPending}>
      <div className="field-group"><label htmlFor="fix-project">Projekt</label><select id="fix-project" className="text-input" value={projectId} required onChange={event => setProjectId(event.target.value)}>
        <option value="" disabled>Wybierz projekt</option>
        {options.map(project => <option key={project.id} value={project.id}>{project.name}{project.archivedAt ? ' (archiwalny)' : ''}</option>)}
      </select></div>
      <label><input type="checkbox" checked={undated} onChange={event => setUndated(event.target.checked)} /> Bez daty</label>
      <div className="field-group"><label htmlFor="fix-date">Data pracy</label><input id="fix-date" className="text-input" type="date" required={!undated} disabled={undated} min={range.min} max={range.max} value={undated ? '' : date} onChange={event => setDate(event.target.value)} /></div>
      <div className="field-group"><p className="duration-label" id="fix-duration-label">Zarejestrowany czas</p><div className="duration-fields" role="group" aria-labelledby="fix-duration-label">
        <div><label htmlFor="fix-hours">Godziny</label><input id="fix-hours" className="text-input" type="number" min={0} step={1} value={hours} onChange={event => setHours(event.target.value)} /></div>
        <div><label htmlFor="fix-minutes">Minuty</label><input id="fix-minutes" className="text-input" type="number" min={0} max={59} step={1} value={minutes} onChange={event => setMinutes(event.target.value)} /></div>
        <div><label htmlFor="fix-seconds">Sekundy</label><input id="fix-seconds" className="text-input" type="number" min={0} max={59} step={1} value={seconds} onChange={event => setSeconds(event.target.value)} /></div>
      </div></div>
      <div className="field-group"><label htmlFor="fix-description">Opis pracy <span className="optional-label">(opcjonalnie)</span></label><textarea id="fix-description" className="text-input project-description-input" rows={3} maxLength={2000} value={description} onChange={event => setDescription(event.target.value)} /></div>
    </fieldset>
    {(validation || save.isError) && <p className="form-error" role="alert">{validation || save.error?.message}</p>}
    <footer className="dialog-actions"><button type="button" className="button-secondary" disabled={save.isPending} onClick={onDone}>Wróć do listy</button>
      <button className="button-primary" disabled={save.isPending}>{save.isPending && <LoaderCircle className="spin" size={16} />}{save.isPending ? 'Zapisywanie…' : entry ? 'Zapisz korektę' : 'Dodaj wpis'}</button></footer>
  </form>
}

export function MemberCorrections({ userId, team, member, onClose }: { userId: string; team: Team; member: TeamMember; onClose: () => void }) {
  const [view, setView] = useState<View>({ type: 'list' })
  const [page, setPage] = useState(0)
  const entries = useMemberTimeEntries(userId, team.id, member.userId, page)
  const remove = useDeleteMemberEntry(userId, team.id, member.userId)
  const back = () => setView({ type: 'list' })
  const title = view.type === 'form' ? (view.entry ? 'Popraw wpis czasu' : 'Dodaj wpis czasu') : view.type === 'delete' ? 'Usuń wpis czasu' : `Korekty czasu: ${member.displayName}`
  return <Modal title={title} busy={remove.isPending} onClose={onClose}>
    {view.type === 'form' && <CorrectionForm userId={userId} team={team} member={member} entry={view.entry} onDone={back} />}
    {view.type === 'delete' && <>
      <p className="dialog-description">Trwale usunąć wpis osoby <strong>{member.displayName}</strong> w projekcie <strong>{view.entry.projectName}</strong> {view.entry.workDate ? <>z dnia <strong>{view.entry.workDate}</strong></> : <strong>bez daty</strong>} o czasie <strong>{formatDuration(view.entry.durationSeconds)}</strong>? Tej operacji nie można cofnąć.</p>
      {remove.isError && <p className="form-error" role="alert">{remove.error.message}</p>}
      <footer className="dialog-actions"><button className="button-secondary" disabled={remove.isPending} onClick={back}>Anuluj</button>
        <button className="button-primary button-danger" disabled={remove.isPending} onClick={() => remove.mutate({ teamId: team.id, id: view.entry.id }, { onSuccess: back })}>{remove.isPending && <LoaderCircle className="spin" size={16} />}{remove.isPending ? 'Usuwanie…' : 'Usuń wpis'}</button></footer>
    </>}
    {view.type === 'list' && <>
      <p className="dialog-description">Zapisane wpisy osoby <strong>{member.displayName}</strong>. Każda zmiana jest oznaczona jako poprawiona przez Ciebie i widoczna dla tej osoby.</p>
      {!member.leftAt && !team.archivedAt && <button className="button-secondary" onClick={() => setView({ type: 'form', entry: null })}><Plus size={15} />Dodaj wpis</button>}
      {entries.isPending ? <p className="teams-loading" role="status"><LoaderCircle size={18} className="spin" />Wczytywanie wpisów…</p>
        : entries.isError ? <div className="dashboard-error" role="alert">{entries.error.message}<button onClick={() => void entries.refetch()}>Wczytaj wpisy ponownie</button></div>
        : entries.data.content.length === 0 ? <p className="report-empty">Ta osoba nie ma jeszcze zapisanych wpisów.</p>
        : <ul className="correction-list" aria-label={`Wpisy osoby ${member.displayName}`}>{entries.data.content.map(entry => <li key={entry.id}>
          <div><strong className="history-project-name"><ProjectDot color={entry.projectColor} />{entry.projectName}</strong> · {entry.workDate ?? 'bez daty'} · {formatDuration(entry.durationSeconds)}
            <p className="history-description">{entry.description || 'Bez opisu'}</p>
            {entry.editedAt && <span className="edited-mark">{entry.editedByName ? `poprawił(a): ${entry.editedByName}` : 'edytowano'}</span>}</div>
          {!team.archivedAt && <div className="history-actions">
            <button className="text-link" aria-label={`Popraw wpis: ${entry.projectName} ${entry.workDate ?? 'bez daty'}`} onClick={() => setView({ type: 'form', entry })}><Pencil size={15} />Popraw</button>
            <button className="text-link" aria-label={`Usuń wpis: ${entry.projectName} ${entry.workDate ?? 'bez daty'}`} onClick={() => setView({ type: 'delete', entry })}><Trash2 size={15} />Usuń</button></div>}
        </li>)}</ul>}
      {entries.data && entries.data.totalPages > 1 && <nav className="history-pagination" aria-label="Strony wpisów"><p>Strona {entries.data.page + 1} z {entries.data.totalPages}</p><div>
        <button className="button-secondary" disabled={page === 0 || entries.isFetching} onClick={() => setPage(page - 1)}><ChevronLeft size={16} />Poprzednia</button>
        <button className="button-secondary" disabled={page + 1 >= entries.data.totalPages || entries.isFetching} onClick={() => setPage(page + 1)}>Następna<ChevronRight size={16} /></button></div></nav>}
      <footer className="dialog-actions"><button className="button-secondary" onClick={onClose}>Zamknij</button></footer>
    </>}
  </Modal>
}
