import { useState, type FormEvent } from 'react'
import { Check, Copy, Link2, LoaderCircle, Plus, Settings, Users } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'
import { Modal } from '../components/Modal'
import type { Team } from '../lib/api'
import { useCreateInvitation, useCreateTeam, useTeams } from '../lib/teams'
import { TeamMembersPanel } from './TeamMembersPanel'
import { TeamSettingsModal } from './TeamSettings'

const roleLabel = (role: Team['role']) => role === 'OWNER' ? 'Właściciel' : 'Członek'
const dateLabel = (date: string) => new Date(date).toLocaleString('pl-PL', { dateStyle: 'medium', timeStyle: 'short' })
const localDateTime = () => {
  const now = new Date()
  return new Date(now.getTime() - now.getTimezoneOffset() * 60_000).toISOString().slice(0, 16)
}

function CreateTeamModal({ userId, onCreated, onClose }: { userId: string; onCreated: (team: Team) => void; onClose: () => void }) {
  const create = useCreateTeam(userId)
  const [name, setName] = useState('')
  const [timezone, setTimezone] = useState(() => Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC')
  const [validation, setValidation] = useState('')
  const timezones = [...new Set([timezone, 'UTC', ...Intl.supportedValuesOf('timeZone')])].sort()
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!name.trim()) { setValidation('Wpisz nazwę zespołu.'); return }
    setValidation('')
    create.mutate({ name: name.trim(), timezone }, { onSuccess: onCreated })
  }
  return <Modal title="Utwórz zespół" busy={create.isPending} onClose={onClose}>
    <p className="dialog-description">Zostaniesz właścicielem nowego zespołu i będziesz móc zapraszać do niego inne osoby.</p>
    <form onSubmit={submit}>
      <fieldset disabled={create.isPending}>
        <div className="field-group"><label htmlFor="team-name">Nazwa zespołu</label><input className="text-input" id="team-name" value={name} maxLength={255} required onChange={event => { setName(event.target.value); setValidation('') }} /></div>
        <div className="field-group"><label htmlFor="team-timezone">Strefa czasowa</label><select className="text-input" id="team-timezone" value={timezone} onChange={event => setTimezone(event.target.value)}>{timezones.map(zone => <option key={zone}>{zone}</option>)}</select><p className="field-hint">Strefa czasowa, w której pracuje zespół.</p></div>
      </fieldset>
      {(validation || create.isError) && <p role="alert" className="form-error">{validation || create.error?.message}</p>}
      <footer className="dialog-actions"><button type="button" className="button-secondary" disabled={create.isPending} onClick={onClose}>Anuluj</button><button type="submit" className="button-primary" disabled={create.isPending}>{create.isPending && <LoaderCircle size={16} className="spin" />}{create.isPending ? 'Tworzenie…' : 'Utwórz zespół'}</button></footer>
    </form>
  </Modal>
}

function InviteModal({ team, onClose }: { team: Team; onClose: () => void }) {
  const invite = useCreateInvitation()
  const [expires, setExpires] = useState('')
  const [limit, setLimit] = useState('')
  const [validation, setValidation] = useState('')
  const [copied, setCopied] = useState(false)
  const [copyError, setCopyError] = useState('')

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const date = new Date(expires)
    if (!Number.isFinite(date.getTime()) || date.getTime() <= Date.now()) {
      setValidation('Wybierz datę wygaśnięcia w przyszłości.'); return
    }
    const maxUses = limit === '' ? null : Number(limit)
    if (maxUses !== null && (!Number.isInteger(maxUses) || maxUses < 1 || maxUses > 2147483647)) {
      setValidation('Limit użyć musi być dodatnią liczbą całkowitą.'); return
    }
    setValidation('')
    invite.mutate({ teamId: team.id, expiresAt: date.toISOString(), maxUses })
  }

  async function copy() {
    if (!invite.data) return
    try {
      await navigator.clipboard.writeText(invite.data.invitationUrl)
      setCopied(true); setCopyError('')
    } catch { setCopyError('Nie udało się skopiować. Zaznacz i skopiuj link ręcznie.') }
  }

  return <Modal title="Zaproś do zespołu" busy={invite.isPending} onClose={onClose}>
    <p className="dialog-description">Zespół: <strong>{team.name}</strong>. Osoba z linkiem będzie mogła dołączyć po zalogowaniu.</p>
    {invite.data ? <div className="invitation-result">
      <label htmlFor="invite-link">Link zaproszeniowy</label>
      <input className="text-input" id="invite-link" value={invite.data.invitationUrl} readOnly onFocus={event => event.currentTarget.select()} />
      <dl className="invite-summary"><dt>Ważny do</dt><dd>{dateLabel(invite.data.expiresAt)}</dd><dt>Limit użyć</dt><dd>{invite.data.maxUses ?? 'Bez limitu'}</dd></dl>
      <p className="field-hint">Przekaż link osobom, które chcesz zaprosić. Skopiuj go przed zamknięciem tego okna.</p>
      {copyError && <p role="alert" className="form-error">{copyError}</p>}
      <footer className="dialog-actions"><button className="button-secondary" onClick={onClose}>Zamknij</button><button className="button-primary" aria-label={copied ? 'Skopiowano link' : 'Kopiuj link'} onClick={() => void copy()}>{copied ? <Check size={16} /> : <Copy size={16} />}{copied ? 'Skopiowano' : 'Kopiuj link'}</button></footer>
      <p className="sr-only" role="status">{copied ? 'Skopiowano link do schowka.' : ''}</p>
    </div> : <form onSubmit={submit}>
      <fieldset disabled={invite.isPending}>
        <div className="field-group"><label htmlFor="invite-expiration">Data wygaśnięcia</label><input className="text-input" id="invite-expiration" type="datetime-local" min={localDateTime()} required value={expires} onChange={event => setExpires(event.target.value)} /><p className="field-hint">Data i godzina w Twojej lokalnej strefie czasowej.</p></div>
        <div className="field-group"><label htmlFor="invite-limit">Limit użyć <span className="optional-label">(opcjonalnie)</span></label><input className="text-input" id="invite-limit" type="number" min={1} max={2147483647} step={1} value={limit} onChange={event => setLimit(event.target.value)} placeholder="Bez limitu" /><p className="field-hint">Pozostaw puste, aby nie ograniczać liczby dołączeń.</p></div>
      </fieldset>
      {(validation || invite.isError) && <p role="alert" className="form-error">{validation || invite.error?.message}</p>}
      <footer className="dialog-actions"><button type="button" className="button-secondary" disabled={invite.isPending} onClick={onClose}>Anuluj</button><button type="submit" className="button-primary" disabled={invite.isPending}>{invite.isPending ? <LoaderCircle size={16} className="spin" /> : <Link2 size={16} />}{invite.isPending ? 'Generowanie…' : 'Generuj link'}</button></footer>
    </form>}
  </Modal>
}

export function TeamsPanel({ userId }: { userId: string }) {
  const teams = useTeams(userId)
  const [search, setSearch] = useSearchParams()
  const [modal, setModal] = useState<'create' | 'invite' | 'settings' | null>(null)
  const [notice, setNotice] = useState('')
  const selectedId = search.get('team')
  const selected = selectedId ? teams.data?.find(team => team.id === selectedId) : teams.data?.[0]
  function select(team: Team) { setNotice(''); setSearch({ team: team.id }, { replace: true }) }

  return <section className="teams-panel" aria-label="Zarządzanie zespołami">
    <header className="teams-toolbar"><div><h2>Twoje zespoły</h2><p>Wybierz zespół, aby zobaczyć swoją rolę i dostępne działania.</p></div><button className="button-primary" onClick={() => setModal('create')}><Plus size={16} />Utwórz zespół</button></header>
    {notice && <p className="project-notice" role="status">{notice}</p>}
    {teams.isPending ? <p role="status" className="teams-loading"><LoaderCircle size={18} className="spin" />Ładowanie zespołów…</p>
      : teams.isError ? <div className="dashboard-error" role="alert">{teams.error.message}<button onClick={() => void teams.refetch()}>Spróbuj ponownie</button></div>
      : teams.data.length === 0 ? <div className="overview-panel empty-state"><Users size={28} strokeWidth={1.4} /><h3>Nie należysz jeszcze do żadnego zespołu.</h3><p>Utwórz własny zespół lub otwórz link zaproszeniowy otrzymany od właściciela.</p></div>
      : <>
        <div className="team-table-wrap"><table className="team-table"><caption className="sr-only">Lista Twoich zespołów</caption><thead><tr><th>Zespół</th><th>Strefa czasowa</th><th>Twoja rola</th></tr></thead><tbody>
          {teams.data.map(team => <tr key={team.id} className={team.id === selected?.id ? 'selected-team-row' : ''}><td><button aria-pressed={team.id === selected?.id} onClick={() => select(team)}>{team.name}{team.id === selected?.id && <Check size={14} aria-label="Wybrany zespół" />}</button>{team.archivedAt && <span className="project-status archived">Zarchiwizowany</span>}</td><td>{team.timezone}</td><td>{roleLabel(team.role)}</td></tr>)}
        </tbody></table></div>
        {selected && <section className="selected-team" aria-label="Wybrany zespół"><div><h3>{selected.name}</h3><p>Twoja rola: {roleLabel(selected.role)}</p>{selected.archivedAt && <p className="field-hint">Zespół jest zarchiwizowany: historia i raporty pozostają dostępne, a nowe wpisy i zmiany są zablokowane.</p>}</div><div className="team-actions"><Link className="button-secondary" to={`/projects?${new URLSearchParams({ team: selected.id })}`}>Projekty zespołu</Link>{selected.role === 'OWNER' ? <>{!selected.archivedAt && <button className="button-secondary" onClick={() => setModal('invite')}><Link2 size={16} />Zaproś do zespołu</button>}<button className="button-secondary" onClick={() => { setNotice(''); setModal('settings') }}><Settings size={16} />Ustawienia zespołu</button></> : <p className="field-hint">Linki zaproszeniowe generuje właściciel zespołu.</p>}</div></section>}
        {selected ? <TeamMembersPanel key={`${selected.id}:${selected.role}`} userId={userId} team={selected} onLeft={() => {
          setModal(null); setNotice(`Opuszczono zespół ${selected.name}. Historia Twojej pracy została zachowana.`); setSearch({}, { replace: true })
        }} /> : <p className="form-error" role="alert">Ten zespół jest niedostępny. Wybierz inny zespół z listy.</p>}
      </>}
    {modal === 'create' && <CreateTeamModal userId={userId} onClose={() => setModal(null)} onCreated={team => { select(team); setModal(null) }} />}
    {modal === 'invite' && selected?.role === 'OWNER' && !selected.archivedAt && <InviteModal key={selected.id} team={selected} onClose={() => setModal(null)} />}
    {modal === 'settings' && selected?.role === 'OWNER' && <TeamSettingsModal key={selected.id} userId={userId} team={selected} onClose={() => setModal(null)}
      onSaved={team => { setModal(null); setNotice(`Zapisano ustawienia zespołu ${team.name}.`) }}
      onArchived={team => { setModal(null); setNotice(`Zespół ${team.name} został zarchiwizowany.`) }}
      onDeleted={() => { setModal(null); setNotice(`Usunięto zespół ${selected.name}.`); setSearch({}, { replace: true }) }} />}
  </section>
}
