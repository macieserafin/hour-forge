import { LoaderCircle } from 'lucide-react'
import { Modal } from '../components/Modal'
import { addDays, formatDuration, teamToday, useCopyDay, useTimeEntryHistory } from '../lib/time'

/** Copies one day of the person's own work to another day (by default yesterday to today) as manual entries. */
export function RepeatDayModal({ userId, teamId, timezone, onClose }: { userId: string; teamId: string; timezone: string; onClose: () => void }) {
  const today = teamToday(timezone)
  const yesterday = addDays(today, -1)
  const copy = useCopyDay(userId)
  const source = useTimeEntryHistory(userId, teamId, { projectId: '', from: yesterday, to: yesterday }, 0)
  const items = source.data?.content ?? []
  const total = items.reduce((sum, entry) => sum + entry.durationSeconds, 0)
  const more = (source.data?.totalPages ?? 0) > 1
  const done = copy.data

  return <Modal title="Powtórz wczorajszy dzień" busy={copy.isPending} onClose={onClose}>
    {done ? <p role="status" className="project-notice">Skopiowano wpisy: {done.copied}. Łączny czas: <strong>{formatDuration(done.totalSeconds)}</strong>. Znajdziesz je w historii pod datą {today}.</p>
      : source.isPending ? <p role="status" className="teams-loading"><LoaderCircle size={18} className="spin" />Wczytywanie wpisów z wczoraj…</p>
      : source.isError ? <p className="form-error" role="alert">{source.error.message}</p>
      : items.length === 0 ? <p className="dialog-description">Wczoraj ({yesterday}) nie masz zapisanych wpisów, więc nie ma czego kopiować.</p>
      : <>
        <p className="dialog-description">Skopiujemy wpisy z dnia <strong>{yesterday}</strong> na dzień <strong>{today}</strong> jako wpisy ręczne (ten sam projekt, opis, czas i zadanie Jira).</p>
        <ul className="repeat-day-list" aria-label="Wpisy do skopiowania">
          {items.map(entry => <li key={entry.id}><strong>{entry.projectName}</strong> · {formatDuration(entry.durationSeconds)}{entry.description ? ` · ${entry.description}` : ''}</li>)}
        </ul>
        <p className="field-hint">{more ? 'To tylko początek listy; skopiowane zostaną wszystkie wpisy z tego dnia. ' : ''}{!more ? `Łącznie: ${formatDuration(total)}. ` : ''}Jeśli na dzisiaj masz już te same wpisy, powstaną ich kopie.</p>
      </>}
    {copy.isError && <p className="form-error" role="alert">{copy.error.message}</p>}
    <footer className="dialog-actions">
      <button type="button" className="button-secondary" disabled={copy.isPending} onClick={onClose}>{done ? 'Zamknij' : 'Anuluj'}</button>
      {!done && items.length > 0 && <button type="button" className="button-primary" disabled={copy.isPending}
        onClick={() => copy.mutate({ teamId, fromDate: yesterday, toDate: today })}>{copy.isPending && <LoaderCircle size={16} className="spin" />}{copy.isPending ? 'Kopiowanie…' : 'Skopiuj wpisy'}</button>}
    </footer>
  </Modal>
}
