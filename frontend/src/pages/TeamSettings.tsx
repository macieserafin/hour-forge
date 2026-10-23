import { projectColorKeys, projectColors, type ProjectColor } from '../lib/projectColors'
import { useState, type FormEvent } from 'react'
import { Archive, LoaderCircle, Trash2 } from 'lucide-react'
import { Modal } from '../components/Modal'
import { PictureEditor } from '../components/PictureEditor'
import { TeamMark } from '../components/Avatar'
import { useTeamLogo } from '../lib/account'
import type { Team } from '../lib/api'
import { useArchiveTeam, useDeleteTeam, useTeamSummary, useUpdateTeam } from '../lib/teams'

type View = 'edit' | 'archive' | 'delete'

function TeamLogoSection({ userId, team }: { userId: string; team: Team }) {
  const { upload, remove } = useTeamLogo(userId)
  const [editing, setEditing] = useState(false)
  // The list refreshes after a change; until then the dialog shows what this session just did.
  const [version, setVersion] = useState(team.logoUpdatedAt ?? null)
  return <section className="team-logo-settings" aria-labelledby="team-logo-title">
    <h3 id="team-logo-title">Logo zespołu <span className="optional-label">(opcjonalnie)</span></h3>
    <p className="field-hint">Logo widać na liście zespołów i w nagłówku raportu zespołu, zamiast kropki z kolorem.</p>
    {editing
      ? <PictureEditor label="Nowe logo zespołu" saving={upload.isPending} error={upload.error?.message}
        onSave={picture => upload.mutate({ teamId: team.id, picture }, { onSuccess: () => { setVersion(new Date().toISOString()); setEditing(false) } })}
        onCancel={() => { upload.reset(); setEditing(false) }} />
      : <div className="picture-current">
        {version && <TeamMark team={{ ...team, logoUpdatedAt: version }} />}
        <button type="button" className="button-secondary" onClick={() => { remove.reset(); setEditing(true) }}>{version ? 'Zmień logo' : 'Dodaj logo'}</button>
        {version && <button type="button" className="button-secondary" disabled={remove.isPending}
          onClick={() => remove.mutate(team.id, { onSuccess: () => setVersion(null) })}>Usuń logo</button>}
      </div>}
    {remove.isError && <p className="form-error" role="alert">{remove.error.message}</p>}
  </section>
}

// Deleting removes the team from the cache, which unmounts this dialog before mutate callbacks would run;
// the mutateAsync promise still settles, so the parent learns about the deletion.
export function TeamSettingsModal({ userId, team, onClose, onSaved, onArchived, onDeleted }: {
  userId: string; team: Team; onClose: () => void; onSaved: (team: Team) => void; onArchived: (team: Team) => void; onDeleted: () => void
}) {
  const [view, setView] = useState<View>('edit')
  const update = useUpdateTeam(userId)
  const archive = useArchiveTeam(userId)
  const remove = useDeleteTeam(userId)
  const summary = useTeamSummary(userId, team.id, view === 'delete')
  const [name, setName] = useState(team.name)
  const [timezone, setTimezone] = useState(team.timezone)
  const [liveView, setLiveView] = useState(team.activeTimersVisible)
  const [requireDescription, setRequireDescription] = useState(team.requireDescription)
  const [requireTagOrIssue, setRequireTagOrIssue] = useState(team.requireTagOrIssue)
  const [weekStart, setWeekStart] = useState(team.weekStart)
  const [weeklyReport, setWeeklyReport] = useState(team.weeklyReport)
  const [color, setColor] = useState<ProjectColor | null>(team.color && team.color in projectColors ? team.color as ProjectColor : null)
  const [confirmation, setConfirmation] = useState('')
  const [validation, setValidation] = useState('')
  const busy = update.isPending || archive.isPending || remove.isPending
  const archived = !!team.archivedAt
  const timezones = [...new Set([team.timezone, 'UTC', ...Intl.supportedValuesOf('timeZone')])].sort()

  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!name.trim()) { setValidation('Wpisz nazwę zespołu.'); return }
    setValidation('')
    update.mutate({ teamId: team.id, name: name.trim(), timezone, activeTimersVisible: liveView, requireDescription, requireTagOrIssue, weekStart, weeklyReport, ...(color ? { color } : { clearColor: true }) }, { onSuccess: onSaved })
  }

  const title = view === 'archive' ? 'Zarchiwizuj zespół' : view === 'delete' ? 'Usuń zespół' : 'Ustawienia zespołu'
  return <Modal title={title} busy={busy} onClose={onClose}>
    {view === 'edit' && <>
      <form onSubmit={save}>
        <fieldset disabled={busy || archived}>
          <div className="field-group"><label htmlFor="settings-name">Nazwa zespołu</label><input className="text-input" id="settings-name" value={name} maxLength={255} required onChange={event => { setName(event.target.value); setValidation('') }} /></div>
          <div className="field-group"><label htmlFor="settings-timezone">Strefa czasowa</label><select className="text-input" id="settings-timezone" value={timezone} onChange={event => setTimezone(event.target.value)}>{timezones.map(zone => <option key={zone}>{zone}</option>)}</select>
            <p className="field-hint">Nowa strefa dotyczy nowych wpisów i bieżących dat. Zapisane daty pracy się nie zmieniają.</p></div>
          <div className="field-group"><label><input type="checkbox" checked={liveView} onChange={event => setLiveView(event.target.checked)} /> Pokaż właścicielowi, kto teraz pracuje</label>
            <p className="field-hint">Podgląd aktywnych timerów w raporcie zespołu: osoba, projekt i czas. Wyłączenie ukrywa go także przed właścicielem.</p></div>
          <fieldset className="color-choice"><legend>Kolor zespołu <span className="optional-label">(opcjonalnie)</span></legend>
            <label className="color-option"><input type="radio" name="team-color" checked={color === null} onChange={() => setColor(null)} /><span className="color-swatch color-none" aria-hidden="true" /><span>Bez koloru</span></label>
            {projectColorKeys.map(key => <label className="color-option" key={key}><input type="radio" name="team-color" checked={color === key} onChange={() => setColor(key)} /><span className="color-swatch" style={{ background: projectColors[key].hex }} aria-hidden="true" /><span>{projectColors[key].label}</span></label>)}
          </fieldset>
          <fieldset className="team-rules"><legend>Reguły zespołu</legend>
            <div className="field-group check-row"><label><input type="checkbox" checked={requireDescription} onChange={event => setRequireDescription(event.target.checked)} /> Wymagaj opisu pracy</label></div>
            <div className="field-group check-row"><label><input type="checkbox" checked={requireTagOrIssue} onChange={event => setRequireTagOrIssue(event.target.checked)} /> Wymagaj tagu lub zadania Jira</label></div>
            <p className="field-hint">Reguły dotyczą wpisów, które członkowie zapisują sami (zatrzymanie timera, wpis ręczny, edycja). Korekty właściciela i administratora są od nich wolne. Wymóg tagu lub zadania działa, dopóki zespół ma aktywny tag albo połączoną Jirę.</p>
            <div className="field-group"><label htmlFor="settings-week">Pierwszy dzień tygodnia w raportach zespołu</label>
              <select className="text-input" id="settings-week" value={weekStart} onChange={event => setWeekStart(event.target.value as 'MONDAY' | 'SUNDAY')}><option value="MONDAY">Poniedziałek</option><option value="SUNDAY">Niedziela</option></select></div>
          </fieldset>
          <div className="field-group check-row"><label><input type="checkbox" checked={weeklyReport} onChange={event => setWeeklyReport(event.target.checked)} /> Wysyłaj cotygodniowy raport PDF e-mailem</label>
            <p className="field-hint">W poniedziałek od 8:00 (czas zespołu) właściciel i administratorzy dostaną raport z poprzedniego tygodnia. Każda osoba może go wyłączyć u siebie w koncie.</p></div>
        </fieldset>
        {archived && <p className="field-hint">Zespół jest zarchiwizowany, więc jego ustawień nie można zmieniać.</p>}
        {(validation || update.isError) && <p className="form-error" role="alert">{validation || update.error?.message}</p>}
        <footer className="dialog-actions"><button type="button" className="button-secondary" disabled={busy} onClick={onClose}>Zamknij</button>
          {!archived && <button type="submit" className="button-primary" disabled={busy}>{update.isPending && <LoaderCircle size={16} className="spin" />}{update.isPending ? 'Zapisywanie…' : 'Zapisz zmiany'}</button>}</footer>
      </form>
      {!archived && <TeamLogoSection userId={userId} team={team} />}
      <section className="danger-zone" aria-labelledby="danger-zone-title">
        <h3 id="danger-zone-title">Operacje nieodwracalne</h3>
        <div className="team-actions">
          {!archived && <button type="button" className="button-secondary" disabled={busy} onClick={() => setView('archive')}><Archive size={15} />Zarchiwizuj zespół</button>}
          {team.role === 'OWNER' && <button type="button" className="button-secondary button-danger-outline" disabled={busy} onClick={() => { setConfirmation(''); setView('delete') }}><Trash2 size={15} />Usuń zespół</button>}
        </div>
      </section>
    </>}

    {view === 'archive' && <>
      <p className="dialog-description">Zarchiwizować zespół <strong>{team.name}</strong>? <strong>Tej operacji nie można cofnąć.</strong></p>
      <ul className="dialog-list">
        <li>Historia pracy i raporty pozostaną dostępne.</li>
        <li>Nie będzie można dodawać ani zmieniać wpisów czasu, projektów, zaproszeń ani ustawień zespołu.</li>
        <li>Wszystkie aktywne timery w zespole muszą być wcześniej zatrzymane.</li>
        <li>Zarchiwizowany zespół można później tylko usunąć.</li>
      </ul>
      {archive.isError && <p className="form-error" role="alert">{archive.error.message}</p>}
      <footer className="dialog-actions"><button className="button-secondary" disabled={busy} onClick={() => { archive.reset(); setView('edit') }}>Wróć</button>
        <button className="button-primary button-danger" disabled={busy} onClick={() => archive.mutate(team.id, { onSuccess: onArchived })}>{archive.isPending && <LoaderCircle size={16} className="spin" />}{archive.isPending ? 'Archiwizowanie…' : 'Zarchiwizuj na stałe'}</button></footer>
    </>}

    {view === 'delete' && <>
      <p className="dialog-description">Trwale usunąć zespół <strong>{team.name}</strong> razem z jego danymi? <strong>Tej operacji nie można cofnąć.</strong></p>
      {summary.isPending ? <p className="teams-loading" role="status"><LoaderCircle size={18} className="spin" />Wczytywanie danych zespołu…</p>
        : summary.isError ? <p className="form-error" role="alert">{summary.error.message}</p>
        : <>
          <dl className="invite-summary"><dt>Aktywni członkowie</dt><dd>{summary.data.activeMembers}</dd><dt>Projekty</dt><dd>{summary.data.projects}</dd><dt>Wpisy czasu</dt><dd>{summary.data.timeEntries}</dd></dl>
          {summary.data.activeTimers && <p className="form-error" role="alert">W zespole działają timery. Poproś członków o ich zatrzymanie przed usunięciem zespołu.</p>}
        </>}
      <div className="field-group"><label htmlFor="delete-confirmation">Wpisz nazwę zespołu, aby potwierdzić</label><input className="text-input" id="delete-confirmation" value={confirmation} autoComplete="off" disabled={busy} onChange={event => setConfirmation(event.target.value)} /></div>
      {remove.isError && <p className="form-error" role="alert">{remove.error.message}</p>}
      <footer className="dialog-actions"><button className="button-secondary" disabled={busy} onClick={() => { remove.reset(); setView('edit') }}>Wróć</button>
        <button className="button-primary button-danger" disabled={busy || confirmation !== team.name || !summary.data || summary.data.activeTimers}
          onClick={() => void remove.mutateAsync(team.id).then(onDeleted, () => { /* Shown through remove.error. */ })}>{remove.isPending && <LoaderCircle size={16} className="spin" />}{remove.isPending ? 'Usuwanie…' : 'Usuń zespół na zawsze'}</button></footer>
    </>}
  </Modal>
}
