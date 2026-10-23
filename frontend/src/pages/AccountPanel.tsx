import { useEffect, useState, type FormEvent } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { LoaderCircle } from 'lucide-react'
import { Modal } from '../components/Modal'
import type { Preferences, User } from '../lib/api'
import { defaultPreferences, usePreferences, useSavePreferences } from '../lib/preferences'
import { useTeams } from '../lib/teams'
import { deviceLabel, passwordChangeError, useChangePassword, useDeleteAccount, useDownloadAccountData, useEndSession, useLoginHistory, useRequestEmailChange, useSessions, useSignOutEverywhere, useUpdateProfile } from '../lib/account'

function ProfileSection({ user }: { user: User }) {
  const save = useUpdateProfile()
  const [name, setName] = useState(user.displayName)
  const [validation, setValidation] = useState('')
  const [saved, setSaved] = useState(false)
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const trimmed = name.trim()
    if (!trimmed || trimmed.length > 255) { setValidation('Wpisz nazwę wyświetlaną (maksymalnie 255 znaków).'); return }
    setValidation(''); setSaved(false)
    save.mutate(trimmed, { onSuccess: () => setSaved(true) })
  }
  return <section className="overview-panel account-section" aria-labelledby="account-profile-title">
    <h2 id="account-profile-title">Profil</h2>
    <p className="field-hint">Adres e-mail: <strong>{user.email}</strong></p>
    <form onSubmit={submit} onInput={() => { setValidation(''); setSaved(false); if (save.isError) save.reset() }}>
      <fieldset disabled={save.isPending}>
        <div className="field-group"><label htmlFor="account-name">Nazwa wyświetlana</label>
          <input className="text-input" id="account-name" autoComplete="name" maxLength={255} required value={name} onChange={event => setName(event.target.value)} /></div>
      </fieldset>
      {(validation || save.isError) && <p className="form-error" role="alert">{validation || save.error?.message}</p>}
      {saved && <p className="project-notice" role="status">Nazwa wyświetlana została zapisana.</p>}
      <button className="button-primary" type="submit" disabled={save.isPending || name.trim() === user.displayName}>
        {save.isPending && <LoaderCircle size={16} className="spin" />}{save.isPending ? 'Zapisywanie…' : 'Zapisz nazwę'}</button>
    </form>
  </section>
}

const hourChoices = [6, 8, 10, 12, 16, 24]

function PreferencesSection({ userId }: { userId: string }) {
  const teams = useTeams(userId)
  const stored = usePreferences(userId)
  const save = useSavePreferences(userId)
  const [draft, setDraft] = useState<Preferences | null>(null)
  const [saved, setSaved] = useState(false)
  const [notificationHint, setNotificationHint] = useState('')
  const current = draft ?? stored.data ?? defaultPreferences
  const hours = hourChoices.includes(current.longTimerHours) ? hourChoices : [...hourChoices, current.longTimerHours].sort((a, b) => a - b)
  const change = (patch: Partial<Preferences>) => { setDraft({ ...current, ...patch }); setSaved(false); setNotificationHint(''); if (save.isError) save.reset() }

  async function toggleNotifications(on: boolean) {
    if (!on) { change({ browserNotifications: false }); return }
    if (!('Notification' in window)) { setNotificationHint('Ta przeglądarka nie obsługuje powiadomień.'); return }
    const permission = Notification.permission === 'default' ? await Notification.requestPermission() : Notification.permission
    if (permission !== 'granted') { setNotificationHint('Przeglądarka nie zezwoliła na powiadomienia. Zmień to w ustawieniach witryny i spróbuj ponownie.'); return }
    change({ browserNotifications: true })
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    save.mutate(current, { onSuccess: () => { setSaved(true); setDraft(null) } })
  }

  return <section className="overview-panel account-section" aria-labelledby="account-preferences-title">
    <h2 id="account-preferences-title">Preferencje</h2>
    <p className="field-hint">Dotyczą tylko Twojego konta, na każdym urządzeniu.</p>
    {stored.isPending ? <p className="teams-loading" role="status"><LoaderCircle size={18} className="spin" />Ładowanie ustawień…</p> : <form onSubmit={submit}>
      <fieldset disabled={save.isPending}>
        <div className="field-group"><label htmlFor="pref-team">Domyślny zespół</label>
          <select id="pref-team" className="text-input" value={current.defaultTeamId ?? ''} onChange={event => change({ defaultTeamId: event.target.value || null })}>
            <option value="">Pierwszy z listy</option>{teams.data?.map(team => <option key={team.id} value={team.id}>{team.name}</option>)}</select>
          <p className="field-hint">Wybrany, gdy w adresie nie ma zespołu: na pulpicie, w rejestrze czasu, projektach i integracjach.</p></div>
        <div className="field-group"><label htmlFor="pref-start">Strona startowa po zalogowaniu</label>
          <select id="pref-start" className="text-input" value={current.startPage} onChange={event => change({ startPage: event.target.value as Preferences['startPage'] })}>
            <option value="DASHBOARD">Pulpit</option><option value="TIME">Rejestr czasu</option><option value="PROJECTS">Projekty</option><option value="TEAMS">Zespoły</option></select></div>
        <div className="field-group"><label htmlFor="pref-format">Zapis czasu</label>
          <select id="pref-format" className="text-input" value={current.timeFormat} onChange={event => change({ timeFormat: event.target.value as Preferences['timeFormat'] })}>
            <option value="CLOCK">Godziny:minuty:sekundy (01:30:00)</option><option value="DECIMAL">Godziny dziesiętnie (1,50 h)</option></select>
          <p className="field-hint">Dotyczy zapisanego czasu. Licznik działającego timera zawsze pokazuje sekundy.</p></div>
        <div className="field-group"><label htmlFor="pref-week">Pierwszy dzień tygodnia</label>
          <select id="pref-week" className="text-input" value={current.weekStart} onChange={event => change({ weekStart: event.target.value as Preferences['weekStart'] })}>
            <option value="MONDAY">Poniedziałek</option><option value="SUNDAY">Niedziela</option></select>
          <p className="field-hint">Dla Twojego pulpitu i zakresów „ten tydzień” w Twoim raporcie.</p></div>
        <div className="field-group"><label className="remember-me"><input type="checkbox" checked={current.titleTimer} onChange={event => change({ titleTimer: event.target.checked })} />Pokazuj czas działającego timera w tytule karty przeglądarki</label></div>
        <h3 className="preferences-heading">Powiadomienia e-mail</h3>
        <p className="field-hint">Wiadomości związane z bezpieczeństwem konta (logowanie, hasło, adres e-mail) wysyłamy zawsze. Pozostałe możesz wyłączyć tutaj albo linkiem w wiadomości.</p>
        <div className="field-group"><label className="remember-me"><input type="checkbox" checked={current.teamEventEmails ?? true} onChange={event => change({ teamEventEmails: event.target.checked })} />Zmiany w zespołach: usunięcie, rola administratora, przekazanie własności, archiwizacja</label></div>
        <div className="field-group"><label className="remember-me"><input type="checkbox" checked={current.weeklyReportEmail ?? true} onChange={event => change({ weeklyReportEmail: event.target.checked })} />Cotygodniowy raport zespołu (dla właścicieli i administratorów)</label></div>
        <h3 className="preferences-heading">Zapomniany timer</h3>
        <div className="field-group"><label htmlFor="pref-hours">Przypomnij, gdy timer działa dłużej niż</label>
          <select id="pref-hours" className="text-input" value={current.longTimerHours} onChange={event => change({ longTimerHours: Number(event.target.value) })}>
            {hours.map(hour => <option key={hour} value={hour}>{hour} godz. czystej pracy</option>)}</select>
          <p className="field-hint">Przerwy się nie liczą. Przypomnienie wysyłamy raz dla każdego timera.</p></div>
        <div className="field-group"><label className="remember-me"><input type="checkbox" checked={current.longTimerEmail} onChange={event => change({ longTimerEmail: event.target.checked })} />Wyślij przypomnienie e-mailem</label></div>
        <div className="field-group"><label className="remember-me"><input type="checkbox" checked={current.browserNotifications} onChange={event => void toggleNotifications(event.target.checked)} />Pokaż powiadomienie w przeglądarce, gdy aplikacja jest otwarta</label>
          {notificationHint && <p className="field-hint" role="status">{notificationHint}</p>}</div>
      </fieldset>
      {save.isError && <p className="form-error" role="alert">{save.error.message}</p>}
      {saved && <p className="project-notice" role="status">Ustawienia zostały zapisane.</p>}
      <button className="button-primary" type="submit" disabled={save.isPending || draft === null}>
        {save.isPending && <LoaderCircle size={16} className="spin" />}{save.isPending ? 'Zapisywanie…' : 'Zapisz ustawienia'}</button>
    </form>}
  </section>
}

function EmailChangeSection() {
  const change = useRequestEmailChange()
  const [newEmail, setNewEmail] = useState('')
  const [password, setPassword] = useState('')
  const [validation, setValidation] = useState('')
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const address = newEmail.trim()
    if (!address || address.length > 255 || !address.includes('@')) { setValidation('Wpisz poprawny nowy adres e-mail.'); return }
    if (!password) { setValidation('Podaj obecne hasło.'); return }
    setValidation('')
    change.mutate({ newEmail: address, currentPassword: password }, { onSuccess: () => setPassword('') })
  }
  return <section className="overview-panel account-section" aria-labelledby="account-email-title">
    <h2 id="account-email-title">Zmiana adresu e-mail</h2>
    <p className="field-hint">Wyślemy link potwierdzający na nowy adres. Po potwierdzeniu zalogujesz się nowym adresem, a stare sesje zostaną zakończone.</p>
    <form onSubmit={submit} onInput={() => { setValidation(''); if (change.isError) change.reset() }}>
      <fieldset disabled={change.isPending}>
        <div className="field-group"><label htmlFor="account-new-email">Nowy adres e-mail</label>
          <input className="text-input" id="account-new-email" type="email" autoComplete="off" maxLength={255} value={newEmail} onChange={event => setNewEmail(event.target.value)} /></div>
        <div className="field-group"><label htmlFor="account-email-password">Hasło do potwierdzenia</label>
          <input className="text-input" id="account-email-password" type="password" autoComplete="current-password" value={password} onChange={event => setPassword(event.target.value)} /></div>
      </fieldset>
      {(validation || change.isError) && <p className="form-error" role="alert">{validation || change.error?.message}</p>}
      {change.isSuccess && <p className="project-notice" role="status">Jeśli adres jest dostępny, wysłaliśmy na niego link potwierdzający.</p>}
      <button className="button-primary" type="submit" disabled={change.isPending}>
        {change.isPending && <LoaderCircle size={16} className="spin" />}{change.isPending ? 'Wysyłanie…' : 'Wyślij link'}</button>
    </form>
  </section>
}

function PasswordSection() {
  const change = useChangePassword()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [validation, setValidation] = useState('')
  const [done, setDone] = useState(false)
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const message = passwordChangeError(current, next, confirmation)
    setValidation(message); setDone(false)
    if (message) return
    change.mutate({ currentPassword: current, newPassword: next }, {
      onSuccess: () => { setCurrent(''); setNext(''); setConfirmation(''); setDone(true) },
    })
  }
  return <section className="overview-panel account-section" aria-labelledby="account-password-title">
    <h2 id="account-password-title">Zmiana hasła</h2>
    <p className="field-hint">Po zmianie hasła wylogujemy Cię na wszystkich innych urządzeniach.</p>
    <form onSubmit={submit} onInput={() => { setValidation(''); setDone(false); if (change.isError) change.reset() }}>
      <fieldset disabled={change.isPending}>
        <div className="field-group"><label htmlFor="account-current-password">Obecne hasło</label>
          <input className="text-input" id="account-current-password" type="password" autoComplete="current-password" value={current} onChange={event => setCurrent(event.target.value)} /></div>
        <div className="field-group"><label htmlFor="account-new-password">Nowe hasło</label>
          <input className="text-input" id="account-new-password" type="password" autoComplete="new-password" maxLength={72} aria-describedby="account-new-password-hint" value={next} onChange={event => setNext(event.target.value)} />
          <p className="field-hint" id="account-new-password-hint">Co najmniej 12 znaków. Możesz używać spacji i znaków specjalnych.</p></div>
        <div className="field-group"><label htmlFor="account-confirm-password">Powtórz nowe hasło</label>
          <input className="text-input" id="account-confirm-password" type="password" autoComplete="new-password" maxLength={72} value={confirmation} onChange={event => setConfirmation(event.target.value)} /></div>
      </fieldset>
      {(validation || change.isError) && <p className="form-error" role="alert">{validation || change.error?.message}</p>}
      {done && <p className="project-notice" role="status">Hasło zostało zmienione. Inne urządzenia zostały wylogowane.</p>}
      <button className="button-primary" type="submit" disabled={change.isPending}>
        {change.isPending && <LoaderCircle size={16} className="spin" />}{change.isPending ? 'Zapisywanie…' : 'Zmień hasło'}</button>
    </form>
  </section>
}

const dateTime = (value: string) => new Date(value).toLocaleString('pl-PL', { dateStyle: 'medium', timeStyle: 'short' })

function DeviceList({ userId }: { userId: string }) {
  const sessions = useSessions(userId)
  const end = useEndSession(userId)
  if (sessions.isPending) return <p role="status"><LoaderCircle size={18} className="spin" />Wczytywanie urządzeń…</p>
  if (sessions.isError) return <div role="alert" className="dashboard-error">{sessions.error.message}
    <button type="button" onClick={() => void sessions.refetch()}>Wczytaj ponownie</button></div>
  return <>
    <ul className="session-list" aria-label="Zalogowane urządzenia">
      {sessions.data.map(session => <li key={session.id}>
        <div>
          <strong>{deviceLabel(session)}</strong>{session.current && <span className="session-current"> · to urządzenie</span>}
          <p className="field-hint">Zalogowano {dateTime(session.signedInAt)} · ostatnia aktywność {dateTime(session.lastActiveAt)}
            {session.address && <> · adres {session.address}</>}{session.location && <> · {session.location}</>}{session.remembered && <> · zapamiętane</>}</p>
        </div>
        {!session.current && <button type="button" className="button-secondary" disabled={end.isPending}
          aria-label={`Wyloguj ${deviceLabel(session)}, zalogowane ${dateTime(session.signedInAt)}`}
          onClick={() => end.mutate(session.id)}>Wyloguj</button>}
      </li>)}
    </ul>
    {end.isError && <p className="form-error" role="alert">{end.error.message}</p>}
    {end.isSuccess && <p className="project-notice" role="status">Urządzenie zostało wylogowane.</p>}
  </>
}

function SignOutSection({ userId }: { userId: string }) {
  const signOut = useSignOutEverywhere()
  const [confirm, setConfirm] = useState(false)
  return <section id="sessions" className="overview-panel account-section" aria-labelledby="account-sessions-title">
    <h2 id="account-sessions-title">Zalogowane urządzenia</h2>
    <p className="field-hint">Wyloguj urządzenie, którego nie rozpoznajesz. Adres jest skrócony, a czasy są przybliżone.</p>
    <DeviceList userId={userId} />
    <p className="field-hint">Możesz też wylogować konto na wszystkich urządzeniach, także na tym. Użyj tego, gdy zgubisz urządzenie lub podejrzewasz, że ktoś ma dostęp do Twojego konta.</p>
    <button className="button-secondary" type="button" onClick={() => { signOut.reset(); setConfirm(true) }}>Wyloguj ze wszystkich urządzeń</button>
    {confirm && <Modal title="Wyloguj ze wszystkich urządzeń" busy={signOut.isPending} onClose={() => setConfirm(false)}>
      <p className="dialog-description">Wszystkie sesje, także ta, zostaną zakończone i trzeba będzie zalogować się ponownie. Aktywny timer nie zostanie zatrzymany.</p>
      {signOut.isError && <p className="form-error" role="alert">{signOut.error.message}</p>}
      <footer className="dialog-actions"><button type="button" className="button-secondary" disabled={signOut.isPending} onClick={() => setConfirm(false)}>Anuluj</button>
        <button type="button" className="button-primary button-danger" disabled={signOut.isPending} onClick={() => signOut.mutate()}>
          {signOut.isPending && <LoaderCircle size={16} className="spin" />}{signOut.isPending ? 'Wylogowywanie…' : 'Wyloguj wszędzie'}</button></footer>
    </Modal>}
  </section>
}

function DataSection() {
  const download = useDownloadAccountData()
  return <section className="overview-panel account-section" aria-labelledby="account-data-title">
    <h2 id="account-data-title">Twoje dane</h2>
    <p className="field-hint">Pobierz kopię swoich danych w formacie JSON: profil, członkostwa w zespołach i wszystkie Twoje wpisy czasu, także z zespołów, które opuściłeś.</p>
    <button className="button-secondary" type="button" disabled={download.isPending} onClick={() => download.mutate()}>
      {download.isPending && <LoaderCircle size={16} className="spin" />}{download.isPending ? 'Przygotowywanie…' : 'Pobierz moje dane'}</button>
    {download.isError && <p className="form-error" role="alert">{download.error.message}</p>}
  </section>
}

function DeleteSection() {
  const remove = useDeleteAccount()
  const [open, setOpen] = useState(false)
  const [password, setPassword] = useState('')
  return <section className="overview-panel account-section" aria-labelledby="account-delete-title">
    <h2 id="account-delete-title">Usuń konto</h2>
    <p className="field-hint">Usuniemy Twoje imię, adres e-mail i hasło. Zapisane godziny pozostaną w raportach zespołów pod nazwą „Użytkownik usunięty”. Najpierw przekaż własność lub usuń zespoły, których jesteś właścicielem.</p>
    <button className="button-secondary" type="button" onClick={() => { remove.reset(); setPassword(''); setOpen(true) }}>Usuń konto</button>
    {open && <Modal title="Usuń konto" busy={remove.isPending} onClose={() => setOpen(false)}>
      <form onSubmit={event => { event.preventDefault(); if (password) remove.mutate(password) }}>
        <p className="dialog-description">Tej operacji nie można cofnąć. Aktywny timer zostanie odrzucony, a Ty opuścisz wszystkie zespoły. Podaj hasło, aby potwierdzić.</p>
        <div className="field-group"><label htmlFor="account-delete-password">Obecne hasło</label>
          <input className="text-input" id="account-delete-password" type="password" autoComplete="current-password" disabled={remove.isPending} value={password} onChange={event => setPassword(event.target.value)} /></div>
        {remove.isError && <p className="form-error" role="alert">{remove.error.message}{remove.error.message.includes('zespoły') && <> <Link className="text-link" to="/teams">Przejdź do zespołów</Link></>}</p>}
        <footer className="dialog-actions"><button type="button" className="button-secondary" disabled={remove.isPending} onClick={() => setOpen(false)}>Anuluj</button>
          <button type="submit" className="button-primary button-danger" disabled={remove.isPending || !password}>
            {remove.isPending && <LoaderCircle size={16} className="spin" />}{remove.isPending ? 'Usuwanie…' : 'Usuń konto na stałe'}</button></footer>
      </form>
    </Modal>}
  </section>
}

function LoginHistorySection({ userId }: { userId: string }) {
  const history = useLoginHistory(userId)
  return <section className="overview-panel account-section" aria-labelledby="account-logins-title">
    <h2 id="account-logins-title">Historia logowań</h2>
    <p className="field-hint">Ostatnie logowania z 90 dni, także nieudane próby. Jeśli widzisz logowanie, którego nie rozpoznajesz, zmień hasło.</p>
    {history.isPending ? <p role="status"><LoaderCircle size={18} className="spin" />Wczytywanie historii logowań…</p>
      : history.isError ? <div role="alert" className="dashboard-error">{history.error.message}
        <button type="button" onClick={() => void history.refetch()}>Wczytaj ponownie</button></div>
      : history.data.entries.length === 0 ? <p>Brak zapisanych logowań.</p>
      : <>
        <div className="team-table-wrap" tabIndex={0} role="region" aria-label="Historia logowań">
          <table className="team-table"><caption className="sr-only">Logowania od najnowszego</caption>
            <thead><tr><th scope="col">Czas</th><th scope="col">Wynik</th><th scope="col">Urządzenie</th><th scope="col">Adres i miejsce</th></tr></thead>
            <tbody>{history.data.entries.map(entry => <tr key={`${entry.occurredAt}-${entry.successful}`}>
              <td><time dateTime={entry.occurredAt}>{dateTime(entry.occurredAt)}</time></td>
              <td>{entry.successful ? 'Udane' : 'Nieudane'}</td>
              <td>{deviceLabel(entry)}</td>
              <td>{[entry.address, entry.location].filter(Boolean).join(' · ') || 'Nieznany'}</td>
            </tr>)}</tbody>
          </table>
        </div>
        {history.data.locationSource && <p className="field-hint">Miejsca są przybliżone. Dane lokalizacji: {history.data.locationSource}.</p>}
      </>}
  </section>
}

export function AccountPanel({ user }: { user: User }) {
  const { hash } = useLocation()
  // The new-device e-mail links to #sessions; the section exists only after the first render.
  useEffect(() => { if (hash) document.getElementById(hash.slice(1))?.scrollIntoView?.() }, [hash])
  return <div className="account-panel">
    <ProfileSection user={user} />
    <PreferencesSection userId={user.id} />
    <EmailChangeSection />
    <PasswordSection />
    <SignOutSection userId={user.id} />
    <LoginHistorySection userId={user.id} />
    <DataSection />
    <DeleteSection />
  </div>
}
