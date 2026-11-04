import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { LoaderCircle } from 'lucide-react'
import { Modal } from '../components/Modal'
import { googleOutcome, type Preferences, type User } from '../lib/api'
import { defaultPreferences, usePreferences, useSavePreferences } from '../lib/preferences'
import { useTeams } from '../lib/teams'
import { deviceLabel, passwordChangeError, useChangePassword, useDeleteAccount, useDownloadAccountData, useEndSession, useLoginHistory, useRequestAccountDeletionByEmail, useRequestEmailChange, useSessions, useSignOutEverywhere, useProfilePicture, useStartGoogleLink, useTwoFactorActions, useTwoFactorStatus, useUnlinkGoogle, useUpdateProfile } from '../lib/account'
import { useGoogleSignIn, useRequestPasswordReset } from '../lib/auth'
import { useActiveTimer } from '../lib/time'
import { Avatar } from '../components/Avatar'
import { PictureEditor } from '../components/PictureEditor'
import { QrCode } from '../components/QrCode'

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

function PictureSection({ user }: { user: User }) {
  const { upload, remove } = useProfilePicture()
  const [editing, setEditing] = useState(false)
  return <section className="overview-panel account-section" aria-labelledby="account-picture-title">
    <h2 id="account-picture-title">Zdjęcie profilowe</h2>
    <p className="field-hint">Widzą je osoby z Twoich zespołów. Zapisujemy tylko mały, przycięty obraz bez danych o miejscu i aparacie.</p>
    {editing
      ? <PictureEditor label="Nowe zdjęcie profilowe" saving={upload.isPending} error={upload.error?.message}
        onSave={picture => upload.mutate(picture, { onSuccess: () => setEditing(false) })} onCancel={() => { upload.reset(); setEditing(false) }} />
      : <div className="picture-current">
        <Avatar user={user} />
        <button type="button" className="button-secondary" onClick={() => { remove.reset(); setEditing(true) }}>{user.avatarUpdatedAt ? 'Zmień zdjęcie' : 'Dodaj zdjęcie'}</button>
        {user.avatarUpdatedAt && <button type="button" className="button-secondary" disabled={remove.isPending} onClick={() => remove.mutate()}>Usuń zdjęcie</button>}
      </div>}
    {remove.isError && <p className="form-error" role="alert">{remove.error.message}</p>}
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

/** Accounts created with Google have no password yet; the usual reset link sets the first one (A13-D6, D7). */
function SetPasswordPrompt({ email, reason }: { email: string; reason: string }) {
  const reset = useRequestPasswordReset()
  return <>
    <p className="field-hint">{reason} Wyślemy link do ustawienia hasła na adres <strong>{email}</strong>.</p>
    {reset.isError && <p className="form-error" role="alert">{reset.error.message}</p>}
    {reset.isSuccess && <p className="project-notice" role="status">Wysłaliśmy link do ustawienia hasła. Po ustawieniu hasła zaloguj się ponownie.</p>}
    <button className="button-secondary" type="button" disabled={reset.isPending} onClick={() => reset.mutate(email)}>
      {reset.isPending && <LoaderCircle size={16} className="spin" />}{reset.isPending ? 'Wysyłanie…' : 'Ustaw hasło'}</button>
  </>
}

function EmailChangeSection({ user }: { user: User }) {
  if (user.hasPassword === false) return <section className="overview-panel account-section" aria-labelledby="account-email-title">
    <h2 id="account-email-title">Zmiana adresu e-mail</h2>
    <SetPasswordPrompt email={user.email} reason="Adres e-mail zmienisz po ustawieniu hasła." />
  </section>
  return <EmailChangeForm />
}

function EmailChangeForm() {
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

function PasswordSection({ user }: { user: User }) {
  if (user.hasPassword === false) return <section className="overview-panel account-section" aria-labelledby="account-password-title">
    <h2 id="account-password-title">Zmiana hasła</h2>
    <SetPasswordPrompt email={user.email} reason="Twoje konto nie ma jeszcze hasła. Logujesz się przez Google." />
  </section>
  return <PasswordForm />
}

function PasswordForm() {
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
            {session.location && <> · {session.location}</>}{session.remembered && <> · zapamiętane</>}</p>
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

function DeleteSection({ user }: { user: User }) {
  const userId = user.id
  const remove = useDeleteAccount(userId)
  const byEmail = useRequestAccountDeletionByEmail()
  const withoutPassword = user.hasPassword === false
  const closeAfterLink = useRef<HTMLButtonElement>(null)
  // The send button disappears once the link is out; keep the focus inside the dialog.
  useEffect(() => { if (byEmail.isSuccess) closeAfterLink.current?.focus() }, [byEmail.isSuccess])
  const runningTimer = useActiveTimer(userId).data
  const [open, setOpen] = useState(false)
  const [password, setPassword] = useState('')
  return <section className="overview-panel account-section" aria-labelledby="account-delete-title">
    <h2 id="account-delete-title">Usuń konto</h2>
    <p className="field-hint">Usuniemy Twoje imię, adres e-mail i hasło. Zapisane godziny pozostaną w raportach zespołów pod nazwą „Użytkownik usunięty”. Trwający timer zostanie odrzucony bez zapisania jego czasu. Najpierw przekaż własność lub usuń zespoły, których jesteś właścicielem.</p>
    <button className="button-secondary" type="button" onClick={() => { remove.reset(); byEmail.reset(); setPassword(''); setOpen(true) }}>Usuń konto</button>
    {open && withoutPassword && <Modal title="Usuń konto" busy={byEmail.isPending} onClose={() => setOpen(false)}>
      <form onSubmit={event => { event.preventDefault(); byEmail.mutate() }}>
        <p className="dialog-description">Tej operacji nie można cofnąć. Twoje konto nie ma hasła, więc wyślemy link potwierdzający na adres <strong>{user.email}</strong>. Konto usuniemy dopiero po otwarciu linku.</p>
        {runningTimer && <p className="form-error">Masz uruchomiony timer. Po usunięciu konta zostanie odrzucony, a jego czas nie zostanie zapisany. Jeśli chcesz zachować te godziny, zatrzymaj timer przed usunięciem konta.</p>}
        {byEmail.isError && <p className="form-error" role="alert">{byEmail.error.message}{byEmail.error.message.includes('zespoły') && <> <Link className="text-link" to="/teams">Przejdź do zespołów</Link></>}</p>}
        {byEmail.isSuccess && <p className="project-notice" role="status">Wysłaliśmy link potwierdzający. Otwórz go, aby usunąć konto.</p>}
        <footer className="dialog-actions"><button ref={closeAfterLink} type="button" className="button-secondary" disabled={byEmail.isPending} onClick={() => setOpen(false)}>{byEmail.isSuccess ? 'Zamknij' : 'Anuluj'}</button>
          {!byEmail.isSuccess && <button type="submit" className="button-primary button-danger" disabled={byEmail.isPending}>
            {byEmail.isPending && <LoaderCircle size={16} className="spin" />}{byEmail.isPending ? 'Wysyłanie…' : 'Wyślij link potwierdzający'}</button>}</footer>
      </form>
    </Modal>}
    {open && !withoutPassword && <Modal title="Usuń konto" busy={remove.isPending} onClose={() => setOpen(false)}>
      <form onSubmit={event => { event.preventDefault(); if (password) remove.mutate(password) }}>
        <p className="dialog-description">Tej operacji nie można cofnąć. Opuścisz wszystkie zespoły, a szkice opisów zapisane w tej przeglądarce zostaną usunięte. Podaj hasło, aby potwierdzić.</p>
        {runningTimer && <p className="form-error">Masz uruchomiony timer. Po usunięciu konta zostanie odrzucony, a jego czas nie zostanie zapisany. Jeśli chcesz zachować te godziny, zatrzymaj timer przed usunięciem konta.</p>}
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

function BackupCodeList({ codes, onDone }: { codes: string[]; onDone: () => void }) {
  const [copied, setCopied] = useState(false)
  return <div className="backup-codes" role="region" aria-label="Kody zapasowe">
    <p className="project-notice" role="status">Zapisz te kody w bezpiecznym miejscu. Każdy działa raz, gdy nie masz dostępu do aplikacji. Nie pokażemy ich ponownie.</p>
    <ul>{codes.map(code => <li key={code}><code>{code}</code></li>)}</ul>
    <div className="dialog-actions">
      <button type="button" className="button-secondary" onClick={() => { void navigator.clipboard?.writeText(codes.join('\n')).then(() => setCopied(true)) }}>
        {copied ? 'Skopiowano' : 'Skopiuj kody'}</button>
      <button type="button" className="button-primary" onClick={onDone}>Zapisałem kody</button>
    </div>
  </div>
}

function PasswordConfirm({ title, description, action, busy, error, onConfirm, onClose }: { title: string; description: string; action: string;
  busy: boolean; error?: string; onConfirm: (password: string) => void; onClose: () => void }) {
  const [password, setPassword] = useState('')
  return <Modal title={title} busy={busy} onClose={onClose}>
    <form onSubmit={event => { event.preventDefault(); if (password) onConfirm(password) }}>
      <p className="dialog-description">{description}</p>
      <div className="field-group"><label htmlFor="two-factor-password">Obecne hasło</label>
        <input className="text-input" id="two-factor-password" type="password" autoComplete="current-password" disabled={busy} value={password} onChange={event => setPassword(event.target.value)} /></div>
      {error && <p className="form-error" role="alert">{error}</p>}
      <footer className="dialog-actions"><button type="button" className="button-secondary" disabled={busy} onClick={onClose}>Anuluj</button>
        <button type="submit" className="button-primary" disabled={busy || !password}>{busy && <LoaderCircle size={16} className="spin" />}{action}</button></footer>
    </form>
  </Modal>
}

function TwoFactorSection({ user }: { user: User }) {
  if (user.hasPassword === false) return <section className="overview-panel account-section" aria-labelledby="account-two-factor-title">
    <h2 id="account-two-factor-title">Logowanie dwuetapowe</h2>
    <SetPasswordPrompt email={user.email} reason="Logowanie dwuetapowe włączysz po ustawieniu hasła." />
  </section>
  return <TwoFactorSettings userId={user.id} />
}

function TwoFactorSettings({ userId }: { userId: string }) {
  const status = useTwoFactorStatus(userId)
  const { setup, enable, disable, renew } = useTwoFactorActions(userId)
  const [code, setCode] = useState('')
  const [password, setPassword] = useState('')
  const [codes, setCodes] = useState<string[] | null>(null)
  const [dialog, setDialog] = useState<'disable' | 'renew' | null>(null)

  function submitEnable(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!code.trim() || !password) return
    enable.mutate({ code, currentPassword: password }, { onSuccess: backupCodes => { setCodes(backupCodes); setup.reset(); setCode(''); setPassword('') } })
  }

  return <section className="overview-panel account-section" aria-labelledby="account-two-factor-title">
    <h2 id="account-two-factor-title">Logowanie dwuetapowe</h2>
    {status.isPending ? <p role="status"><LoaderCircle size={18} className="spin" />Wczytywanie…</p>
      : status.isError ? <div role="alert" className="dashboard-error">{status.error.message}
        <button type="button" onClick={() => void status.refetch()}>Wczytaj ponownie</button></div>
      : codes ? <BackupCodeList codes={codes} onDone={() => setCodes(null)} />
      : status.data.enabled ? <>
        <p className="field-hint">Logowanie dwuetapowe jest włączone. Przy logowaniu podajesz hasło i kod z aplikacji. Pozostałe kody zapasowe: <strong>{status.data.backupCodesLeft}</strong>.</p>
        <div className="dialog-actions">
          <button type="button" className="button-secondary" onClick={() => { renew.reset(); setDialog('renew') }}>Utwórz nowe kody zapasowe</button>
          <button type="button" className="button-secondary" onClick={() => { disable.reset(); setDialog('disable') }}>Wyłącz logowanie dwuetapowe</button>
        </div>
      </>
      : !status.data.available ? <p className="field-hint">Logowanie dwuetapowe nie jest włączone na tym serwerze. Skontaktuj się z administratorem serwisu.</p>
      : setup.data ? <form onSubmit={submitEnable} aria-label="Włączanie logowania dwuetapowego">
        <p className="field-hint">1. Zeskanuj kod QR aplikacją uwierzytelniającą (np. Google Authenticator, Microsoft Authenticator, Aegis) albo wpisz w niej klucz ręcznie.</p>
        <div className="two-factor-setup">
          <QrCode value={setup.data.uri} label="Kod QR do dodania konta HourForge w aplikacji uwierzytelniającej" />
          <p className="field-hint">Klucz: <code className="two-factor-secret">{setup.data.secret.match(/.{1,4}/g)?.join(' ')}</code></p>
        </div>
        <p className="field-hint">2. Wpisz kod pokazany w aplikacji i obecne hasło.</p>
        <div className="field-group"><label htmlFor="two-factor-setup-code">Kod z aplikacji</label>
          <input className="text-input" id="two-factor-setup-code" inputMode="numeric" autoComplete="one-time-code" maxLength={8} value={code} disabled={enable.isPending}
            onChange={event => { setCode(event.target.value); if (enable.isError) enable.reset() }} /></div>
        <div className="field-group"><label htmlFor="two-factor-setup-password">Obecne hasło</label>
          <input className="text-input" id="two-factor-setup-password" type="password" autoComplete="current-password" value={password} disabled={enable.isPending}
            onChange={event => { setPassword(event.target.value); if (enable.isError) enable.reset() }} /></div>
        {enable.isError && <p className="form-error" role="alert">{enable.error.message}</p>}
        <div className="dialog-actions">
          <button type="button" className="button-secondary" disabled={enable.isPending} onClick={() => { setup.reset(); enable.reset() }}>Anuluj</button>
          <button type="submit" className="button-primary" disabled={enable.isPending || !code.trim() || !password}>
            {enable.isPending && <LoaderCircle size={16} className="spin" />}Włącz</button>
        </div>
      </form>
      : <>
        <p className="field-hint">Chroń konto drugim krokiem logowania: po haśle podasz kod z aplikacji uwierzytelniającej w telefonie.</p>
        <button type="button" className="button-secondary" disabled={setup.isPending} onClick={() => setup.mutate()}>
          {setup.isPending && <LoaderCircle size={16} className="spin" />}Włącz logowanie dwuetapowe</button>
        {setup.isError && <p className="form-error" role="alert">{setup.error.message}</p>}
      </>}
    {dialog === 'disable' && <PasswordConfirm title="Wyłącz logowanie dwuetapowe" action="Wyłącz"
      description="Do logowania wystarczy wtedy samo hasło. Kody zapasowe przestaną działać."
      busy={disable.isPending} error={disable.error?.message}
      onConfirm={currentPassword => disable.mutate(currentPassword, { onSuccess: () => setDialog(null) })} onClose={() => setDialog(null)} />}
    {dialog === 'renew' && <PasswordConfirm title="Nowe kody zapasowe" action="Utwórz kody"
      description="Dotychczasowe kody zapasowe przestaną działać."
      busy={renew.isPending} error={renew.error?.message}
      onConfirm={currentPassword => renew.mutate(currentPassword, { onSuccess: backupCodes => { setDialog(null); setCodes(backupCodes) } })} onClose={() => setDialog(null)} />}
  </section>
}

function GoogleSection({ user }: { user: User }) {
  const start = useStartGoogleLink()
  const unlink = useUnlinkGoogle()
  const [search, setSearch] = useSearchParams()
  const [dialog, setDialog] = useState<'link' | 'unlink' | null>(null)
  const [unlinked, setUnlinked] = useState(false)
  const outcome = googleOutcome(search.get('google'), 'link')
  const linked = user.google?.linked === true
  const hasPassword = user.hasPassword !== false

  // The answer from Google stays in the address until the next action here.
  function clearOutcome() {
    setUnlinked(false)
    if (!search.has('google')) return
    const next = new URLSearchParams(search)
    next.delete('google')
    setSearch(next, { replace: true })
  }

  function connect() {
    clearOutcome(); start.reset()
    // D2: the password confirms the link; an account without one is already signed in through Google.
    if (hasPassword) setDialog('link')
    else start.mutate(undefined)
  }

  return <section id="google" className="overview-panel account-section" aria-labelledby="account-google-title">
    <h2 id="account-google-title">Logowanie przez Google</h2>
    {outcome && (outcome.tone === 'success'
      ? <p className="project-notice" role="status">{outcome.text}</p>
      : <p className="form-error" role="alert">{outcome.text}</p>)}
    {unlinked && <p className="project-notice" role="status">Konto Google zostało odłączone.</p>}
    {linked ? <>
      <p className="field-hint">Połączone z kontem Google{user.google?.email && <> <strong>{user.google.email}</strong></>}
        {user.google?.linkedAt && <> od <time dateTime={user.google.linkedAt}>{dateTime(user.google.linkedAt)}</time></>}.</p>
      {hasPassword
        ? <button type="button" className="button-secondary" onClick={() => { clearOutcome(); unlink.reset(); setDialog('unlink') }}>Odłącz Google</button>
        : <p className="field-hint">Nie możesz odłączyć Google, dopóki nie ustawisz hasła.</p>}
    </> : <>
      <p className="field-hint">Połącz konto Google, aby logować się przyciskiem „Zaloguj się przez Google”. Adres e-mail konta się nie zmieni.</p>
      <button type="button" className="button-secondary" disabled={start.isPending} onClick={connect}>
        {start.isPending && dialog === null && <LoaderCircle size={16} className="spin" />}Połącz z Google</button>
      {dialog === null && start.isError && <p className="form-error" role="alert">{start.error.message}</p>}
    </>}
    {dialog === 'link' && <PasswordConfirm title="Połącz z Google" action="Przejdź do Google"
      description="Po potwierdzeniu hasła przejdziesz do Google i wybierzesz konto do połączenia."
      busy={start.isPending} error={start.error?.message}
      onConfirm={currentPassword => start.mutate(currentPassword)} onClose={() => setDialog(null)} />}
    {dialog === 'unlink' && <PasswordConfirm title="Odłącz Google" action="Odłącz"
      description="Nie zalogujesz się już przez Google. Inne urządzenia zostaną wylogowane."
      busy={unlink.isPending} error={unlink.error?.message}
      onConfirm={currentPassword => unlink.mutate(currentPassword, { onSuccess: () => { setDialog(null); setUnlinked(true) } })} onClose={() => setDialog(null)} />}
  </section>
}

const methodLabel = (method: 'PASSWORD' | 'GOOGLE') => ({ PASSWORD: 'Hasło', GOOGLE: 'Google' })[method]

function LoginHistorySection({ userId }: { userId: string }) {
  const history = useLoginHistory(userId)
  const google = useGoogleSignIn()
  // D11: the sign-in method is a column only where the server reports it.
  const methods = google && history.data?.entries.some(entry => entry.method) === true
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
            <thead><tr><th scope="col">Czas</th><th scope="col">Wynik</th>{methods && <th scope="col">Metoda</th>}<th scope="col">Urządzenie</th><th scope="col">Miejsce</th></tr></thead>
            <tbody>{history.data.entries.map(entry => <tr key={`${entry.occurredAt}-${entry.successful}`}>
              <td><time dateTime={entry.occurredAt}>{dateTime(entry.occurredAt)}</time></td>
              <td>{entry.successful ? 'Udane' : 'Nieudane'}</td>
              {methods && <td>{entry.method ? methodLabel(entry.method) : '–'}</td>}
              <td>{deviceLabel(entry)}</td>
              <td>{entry.location || 'Nieznane'}</td>
            </tr>)}</tbody>
          </table>
        </div>
        {history.data.locationSource && <p className="field-hint">Miejsca są przybliżone. Dane lokalizacji: {history.data.locationSource}.</p>}
      </>}
  </section>
}

export function AccountPanel({ user }: { user: User }) {
  const { hash } = useLocation()
  const google = useGoogleSignIn()
  // The new-device e-mail links to #sessions; the section exists only after the first render.
  useEffect(() => { if (hash) document.getElementById(hash.slice(1))?.scrollIntoView?.() }, [hash])
  return <div className="account-panel">
    <ProfileSection user={user} />
    <PictureSection user={user} />
    <PreferencesSection userId={user.id} />
    <EmailChangeSection user={user} />
    <PasswordSection user={user} />
    <TwoFactorSection user={user} />
    {google && <GoogleSection user={user} />}
    <SignOutSection userId={user.id} />
    <LoginHistorySection userId={user.id} />
    <DataSection />
    <DeleteSection user={user} />
  </div>
}
