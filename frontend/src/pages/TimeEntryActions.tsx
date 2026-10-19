import { useState, type FormEvent } from 'react'
import { LoaderCircle } from 'lucide-react'
import { Modal } from '../components/Modal'
import type { Project, TimeEntryHistoryItem } from '../lib/api'
import { formatDuration, useDeleteTimeEntry, useUpdateTimeEntry } from '../lib/time'

export function EditTimeEntry({ userId, entry, projects, onClose }: {
  userId: string; entry: TimeEntryHistoryItem; projects: Project[]; onClose: () => void
}) {
  const save = useUpdateTimeEntry(userId)
  const [projectId, setProjectId] = useState(entry.projectId)
  const [date, setDate] = useState(entry.workDate)
  const [hours, setHours] = useState(String(Math.floor(entry.durationSeconds / 3600)))
  const [minutes, setMinutes] = useState(String(Math.floor(entry.durationSeconds / 60) % 60))
  const [seconds, setSeconds] = useState(String(entry.durationSeconds % 60))
  const [description, setDescription] = useState(entry.description ?? '')
  const [validation, setValidation] = useState('')
  const options = projects.filter(project => !project.archivedAt || project.id === entry.projectId)
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const parts = [hours, minutes, seconds].map(value => value === '' ? 0 : Number(value))
    const duration = parts[0]! * 3600 + parts[1]! * 60 + parts[2]!
    if (!date || !options.some(project => project.id === projectId)) { setValidation('Wybierz projekt i datę pracy.'); return }
    if (parts.some(value => !Number.isInteger(value) || value < 0) || parts[1]! > 59 || parts[2]! > 59 || duration < 1 || duration > 2147483647) {
      setValidation('Podaj dodatni czas w pełnych sekundach. Minuty i sekundy muszą mieścić się w zakresie 0–59.'); return
    }
    setValidation('')
    save.mutate({ id: entry.id, projectId, workDate: date, durationSeconds: duration, description: description.trim() || null }, { onSuccess: onClose })
  }
  return <Modal title="Edytuj wpis czasu" busy={save.isPending} onClose={onClose}>
    <p className="dialog-description">Popraw zapisany czas pracy.{entry.entryType === 'TIMER' && ' Korekta czasu nie zmienia oryginalnych momentów uruchomienia i zakończenia timera.'}</p>
    <form onSubmit={submit} onInput={() => setValidation('')}>
      <fieldset disabled={save.isPending}>
        <div className="field-group"><label htmlFor="edit-project">Projekt</label><select id="edit-project" className="text-input" value={projectId} required onChange={event => setProjectId(event.target.value)}>
          {options.map(project => <option key={project.id} value={project.id}>{project.name}{project.archivedAt ? ' (archiwalny)' : ''}</option>)}
        </select></div>
        <div className="field-group"><label htmlFor="edit-date">Data pracy</label><input id="edit-date" className="text-input" type="date" required value={date} onChange={event => setDate(event.target.value)} /></div>
        <div className="field-group"><p className="duration-label" id="edit-duration-label">Zarejestrowany czas</p><div className="duration-fields" role="group" aria-labelledby="edit-duration-label">
          <div><label htmlFor="edit-hours">Godziny</label><input id="edit-hours" className="text-input" type="number" min={0} step={1} value={hours} onChange={event => setHours(event.target.value)} /></div>
          <div><label htmlFor="edit-minutes">Minuty</label><input id="edit-minutes" className="text-input" type="number" min={0} max={59} step={1} value={minutes} onChange={event => setMinutes(event.target.value)} /></div>
          <div><label htmlFor="edit-seconds">Sekundy</label><input id="edit-seconds" className="text-input" type="number" min={0} max={59} step={1} value={seconds} onChange={event => setSeconds(event.target.value)} /></div>
        </div></div>
        <div className="field-group"><label htmlFor="edit-description">Opis pracy <span className="optional-label">(opcjonalnie)</span></label><textarea id="edit-description" className="text-input project-description-input" rows={3} value={description} onChange={event => setDescription(event.target.value)} /></div>
      </fieldset>
      {(validation || save.isError) && <p className="form-error" role="alert">{validation || save.error?.message}</p>}
      <footer className="dialog-actions"><button type="button" className="button-secondary" disabled={save.isPending} onClick={onClose}>Anuluj</button><button className="button-primary" disabled={save.isPending}>{save.isPending && <LoaderCircle className="spin" size={16} />}{save.isPending ? 'Zapisywanie…' : 'Zapisz zmiany'}</button></footer>
    </form>
  </Modal>
}

export function DeleteTimeEntry({ userId, entry, onClose, onDeleted }: {
  userId: string; entry: TimeEntryHistoryItem; onClose: () => void; onDeleted: () => void
}) {
  const remove = useDeleteTimeEntry(userId)
  return <Modal title="Usuń wpis czasu" busy={remove.isPending} onClose={onClose}>
    <p className="dialog-description">Trwale usunąć wpis w projekcie <strong>{entry.projectName}</strong> z dnia <strong>{entry.workDate}</strong> o czasie <strong>{formatDuration(entry.durationSeconds)}</strong>? Tej operacji nie można cofnąć.</p>
    {remove.isError && <p className="form-error" role="alert">{remove.error.message}</p>}
    <footer className="dialog-actions"><button className="button-secondary" disabled={remove.isPending} onClick={onClose}>Anuluj</button>
      <button className="button-primary button-danger" disabled={remove.isPending} onClick={() => remove.mutate(entry.id, { onSuccess: onDeleted })}>{remove.isPending && <LoaderCircle className="spin" size={16} />}{remove.isPending ? 'Usuwanie…' : 'Usuń wpis'}</button></footer>
  </Modal>
}
