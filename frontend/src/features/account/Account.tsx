import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Database, KeyRound, Palette, ShieldCheck, UserRound } from 'lucide-react'
import { Link, NavLink, Outlet, useLocation, useSearchParams } from 'react-router-dom'
import { useI18n, useMessages, type Locale } from '../../content/i18n'
import { useUser } from '../../app/session'
import { Avatar } from '../../app/Shell'
import { useTheme, type ThemeChoice } from '../../app/theme'
import { useScrollToHash } from '../../lib/scrollToHash'
import { googleOutcome, type Preferences, type User } from '../../lib/api'
import {
  deviceLabel,
  passwordChangeError,
  useChangePassword,
  useDeleteAccount,
  useDownloadAccountData,
  useEndSession,
  useLoginHistory,
  useProfilePicture,
  useRequestAccountDeletionByEmail,
  useRequestEmailChange,
  useSessions,
  useSignOutEverywhere,
  useStartGoogleLink,
  useTwoFactorActions,
  useTwoFactorStatus,
  useUnlinkGoogle,
  useUpdateProfile,
} from '../../lib/account'
import { useGoogleSignIn, useRequestPasswordReset } from '../../lib/auth'
import { defaultPreferences, usePreferences, useSavePreferences } from '../../lib/preferences'
import { useTeams } from '../../lib/teams'
import { useActiveTimer } from '../../lib/time'
import { Button, Dialog, Field, LoadError, Loading, Notice } from '../../ui'
import { PictureEditor } from '../../ui/PictureEditor'
import { QrCode } from '../../ui/QrCode'
import { useToast } from '../../ui/toast'
import './account.css'

const messages = {
  pl: {
    title: 'Konto',
    profile: 'Profil',
    preferences: 'Wygląd i działanie',
    security: 'Bezpieczeństwo',
    data: 'Dane i konto',
    name: 'Nazwa wyświetlana',
    nameHint: 'Widzą ją osoby w Twoich zespołach.',
    save: 'Zapisz',
    saving: 'Zapisywanie…',
    saved: 'Zapisano.',
    needName: 'Wpisz nazwę (do 255 znaków).',
    email: 'Adres e-mail',
    picture: 'Zdjęcie profilowe',
    addPicture: 'Dodaj zdjęcie',
    changePicture: 'Zmień zdjęcie',
    removePicture: 'Usuń zdjęcie',
    newPicture: 'Nowe zdjęcie profilowe',
    look: 'Wygląd',
    theme: 'Motyw',
    system: 'Zgodny z systemem',
    light: 'Jasny',
    dark: 'Ciemny',
    language: 'Język',
    localNote: 'Motyw i język zapisujemy w tej przeglądarce.',
    work: 'Praca z czasem',
    defaultTeam: 'Domyślny zespół',
    firstTeam: 'Wszystkie zespoły',
    defaultTeamHint: 'Zespół wybrany, gdy adres nie wskazuje żadnego.',
    start: 'Strona po zalogowaniu',
    startWork: 'Moja praca',
    startProjects: 'Projekty',
    startTeams: 'Zespoły',
    format: 'Zapis czasu',
    clock: 'Godziny i minuty (1 h 30 min)',
    decimal: 'Godziny dziesiętnie (1,5 h)',
    formatHint: 'Działający timer zawsze pokazuje sekundy.',
    weekStart: 'Pierwszy dzień tygodnia',
    monday: 'Poniedziałek',
    sunday: 'Niedziela',
    titleTimer: 'Pokazuj czas działającego timera w tytule karty przeglądarki',
    notifications: 'Powiadomienia',
    notificationsHint:
      'Wiadomości o bezpieczeństwie konta wysyłamy zawsze. Pozostałe możesz wyłączyć tutaj albo linkiem w wiadomości.',
    teamEvents: 'Zmiany w zespołach: usunięcie, rola administratora, przekazanie własności, archiwizacja',
    weekly: 'Cotygodniowy raport zespołu (dla właścicieli i administratorów)',
    longTimer: 'Zapomniany timer',
    longTimerAfter: 'Przypomnij, gdy timer działa dłużej niż',
    hours: '{hours} godz. pracy',
    longTimerHint: 'Przerwy się nie liczą. Przypomnienie dostaniesz raz dla każdego timera.',
    longTimerEmail: 'Wyślij przypomnienie e-mailem',
    browserNote: 'Pokaż powiadomienie w przeglądarce, gdy aplikacja jest otwarta',
    noNotifications: 'Ta przeglądarka nie obsługuje powiadomień.',
    denied: 'Przeglądarka nie zezwoliła na powiadomienia. Zmień to w ustawieniach witryny.',
    savePrefs: 'Zapisz ustawienia',
    prefsSaved: 'Ustawienia zapisane.',
    password: 'Hasło',
    currentPassword: 'Obecne hasło',
    newPassword: 'Nowe hasło',
    repeat: 'Powtórz nowe hasło',
    passwordHint: 'Co najmniej 12 znaków. Spacje i znaki specjalne są dozwolone.',
    changePassword: 'Zmień hasło',
    passwordChanged: 'Hasło zmienione. Inne urządzenia zostały wylogowane.',
    passwordNote: 'Po zmianie hasła wylogujemy Cię na innych urządzeniach.',
    noPassword: 'Twoje konto nie ma jeszcze hasła. Logujesz się przez Google.',
    setPassword: 'Ustaw hasło',
    setPasswordSent: 'Wysłaliśmy link do ustawienia hasła na {email}.',
    sending: 'Wysyłanie…',
    emailChange: 'Zmiana adresu e-mail',
    newEmail: 'Nowy adres e-mail',
    emailHint:
      'Wyślemy link na nowy adres. Po potwierdzeniu zalogujesz się nowym adresem, a stare sesje zostaną zakończone.',
    sendLink: 'Wyślij link',
    emailSent: 'Jeśli adres jest dostępny, wysłaliśmy na niego link potwierdzający.',
    badEmail: 'Wpisz poprawny nowy adres e-mail.',
    needPassword: 'Podaj obecne hasło.',
    emailAfterPassword: 'Adres zmienisz po ustawieniu hasła.',
    twoFactor: 'Logowanie dwuetapowe',
    twoFactorOff: 'Chroń konto drugim krokiem: po haśle podasz kod z aplikacji uwierzytelniającej.',
    twoFactorOn: 'Włączone. Pozostałe kody zapasowe: {count}.',
    turnOn: 'Włącz logowanie dwuetapowe',
    turnOff: 'Wyłącz',
    unavailable2fa: 'Logowanie dwuetapowe nie jest dostępne na tym serwerze.',
    scan: '1. Zeskanuj kod QR aplikacją uwierzytelniającą albo wpisz klucz ręcznie.',
    key: 'Klucz',
    enterCode: '2. Wpisz kod z aplikacji i obecne hasło.',
    code: 'Kod z aplikacji',
    enable: 'Włącz',
    qr: 'Kod QR do dodania konta HourForge w aplikacji uwierzytelniającej',
    backup: 'Kody zapasowe',
    backupNote:
      'Zapisz te kody w bezpiecznym miejscu. Każdy działa raz, gdy nie masz telefonu. Nie pokażemy ich ponownie.',
    copyCodes: 'Skopiuj kody',
    copied: 'Skopiowano',
    savedCodes: 'Zapisałem kody',
    renew: 'Nowe kody zapasowe',
    renewBody: 'Dotychczasowe kody zapasowe przestaną działać.',
    offBody: 'Do logowania wystarczy wtedy samo hasło. Kody zapasowe przestaną działać.',
    twoFactorAfterPassword: 'Logowanie dwuetapowe włączysz po ustawieniu hasła.',
    google: 'Logowanie przez Google',
    googleLinked: 'Połączone z kontem Google {email}.',
    googleOff:
      'Połącz konto Google, aby logować się przyciskiem „Zaloguj się przez Google”. Adres e-mail konta się nie zmieni.',
    link: 'Połącz z Google',
    unlink: 'Odłącz Google',
    linkBody: 'Po potwierdzeniu hasła przejdziesz do Google i wybierzesz konto do połączenia.',
    goGoogle: 'Przejdź do Google',
    unlinkBody: 'Nie zalogujesz się już przez Google. Inne urządzenia zostaną wylogowane.',
    unlinked: 'Konto Google odłączone.',
    needPasswordFirst: 'Nie możesz odłączyć Google, dopóki nie ustawisz hasła.',
    devices: 'Zalogowane urządzenia',
    devicesHint: 'Wyloguj urządzenie, którego nie rozpoznajesz. Adresy są skrócone, a czasy przybliżone.',
    thisDevice: 'to urządzenie',
    signedIn: 'Zalogowano {date}',
    lastActive: 'aktywność {date}',
    remembered: 'zapamiętane',
    signOutDevice: 'Wyloguj',
    signOutLabel: 'Wyloguj {device}, zalogowane {date}',
    deviceOut: 'Urządzenie wylogowane.',
    everywhere: 'Wyloguj ze wszystkich urządzeń',
    everywhereBody: 'Wszystkie sesje, także ta, zostaną zakończone. Aktywny timer nie zostanie zatrzymany.',
    everywhereDo: 'Wyloguj wszędzie',
    logins: 'Historia logowań',
    loginsHint: 'Logowania z ostatnich 90 dni, także nieudane. Jeśli nie rozpoznajesz logowania, zmień hasło.',
    when: 'Czas',
    result: 'Wynik',
    ok: 'Udane',
    failed: 'Nieudane',
    method: 'Metoda',
    device: 'Urządzenie',
    place: 'Miejsce',
    unknown: 'Nieznane',
    noLogins: 'Brak zapisanych logowań.',
    source: 'Miejsca są przybliżone. Dane lokalizacji: {source}.',
    export: 'Kopia Twoich danych',
    exportHint: 'Plik JSON z profilem, członkostwami i wszystkimi Twoimi wpisami, także z zespołów, które opuściłeś.',
    download: 'Pobierz moje dane',
    preparing: 'Przygotowywanie…',
    delete: 'Usuń konto',
    deleteHint:
      'Usuniemy Twoje imię, adres e-mail i dane logowania. Zapisane godziny zostaną w raportach zespołów jako „Użytkownik usunięty”. Najpierw przekaż własność lub usuń zespoły, których jesteś właścicielem.',
    deleteBody: 'Tej operacji nie można cofnąć. Opuścisz wszystkie zespoły. Podaj hasło, aby potwierdzić.',
    deleteNoPassword:
      'Tej operacji nie można cofnąć. Twoje konto nie ma hasła, więc wyślemy link potwierdzający na {email}. Konto usuniemy dopiero po otwarciu linku.',
    runningTimer:
      'Masz uruchomiony timer. Po usunięciu konta zostanie odrzucony bez zapisu. Zatrzymaj go, jeśli chcesz zachować te godziny.',
    deleteDo: 'Usuń konto na stałe',
    deleting: 'Usuwanie…',
    sendDelete: 'Wyślij link potwierdzający',
    deleteSent: 'Wysłaliśmy link. Otwórz go, aby usunąć konto.',
    teamsLink: 'Przejdź do zespołów',
  },
  en: {
    title: 'Account',
    profile: 'Profile',
    preferences: 'Look and behaviour',
    security: 'Security',
    data: 'Data and account',
    name: 'Display name',
    nameHint: 'People in your teams see it.',
    save: 'Save',
    saving: 'Saving…',
    saved: 'Saved.',
    needName: 'Enter a name (up to 255 characters).',
    email: 'E-mail address',
    picture: 'Profile picture',
    addPicture: 'Add picture',
    changePicture: 'Change picture',
    removePicture: 'Remove picture',
    newPicture: 'New profile picture',
    look: 'Look',
    theme: 'Theme',
    system: 'Follow the system',
    light: 'Light',
    dark: 'Dark',
    language: 'Language',
    localNote: 'Theme and language are saved in this browser.',
    work: 'Working with time',
    defaultTeam: 'Default team',
    firstTeam: 'All teams',
    defaultTeamHint: 'The team chosen when the address names none.',
    start: 'Page after sign-in',
    startWork: 'My work',
    startProjects: 'Projects',
    startTeams: 'Teams',
    format: 'Time format',
    clock: 'Hours and minutes (1 h 30 min)',
    decimal: 'Decimal hours (1.5 h)',
    formatHint: 'A running timer always shows seconds.',
    weekStart: 'First day of the week',
    monday: 'Monday',
    sunday: 'Sunday',
    titleTimer: 'Show the running timer in the browser tab title',
    notifications: 'Notifications',
    notificationsHint:
      'Account security messages are always sent. Others can be turned off here or with the link in the message.',
    teamEvents: 'Team changes: removal, admin role, ownership transfer, archiving',
    weekly: 'Weekly team report (for owners and admins)',
    longTimer: 'Forgotten timer',
    longTimerAfter: 'Remind me when the timer runs longer than',
    hours: '{hours} h of work',
    longTimerHint: 'Breaks do not count. You get one reminder per timer.',
    longTimerEmail: 'Send a reminder by e-mail',
    browserNote: 'Show a browser notification while the app is open',
    noNotifications: 'This browser does not support notifications.',
    denied: 'The browser did not allow notifications. Change it in the site settings.',
    savePrefs: 'Save settings',
    prefsSaved: 'Settings saved.',
    password: 'Password',
    currentPassword: 'Current password',
    newPassword: 'New password',
    repeat: 'Repeat the new password',
    passwordHint: 'At least 12 characters. Spaces and special characters are fine.',
    changePassword: 'Change password',
    passwordChanged: 'Password changed. Other devices were signed out.',
    passwordNote: 'After a change you are signed out on other devices.',
    noPassword: 'Your account has no password yet. You sign in with Google.',
    setPassword: 'Set a password',
    setPasswordSent: 'We sent a link to set a password to {email}.',
    sending: 'Sending…',
    emailChange: 'E-mail change',
    newEmail: 'New e-mail address',
    emailHint: 'We send a link to the new address. After confirming you sign in with it and old sessions end.',
    sendLink: 'Send link',
    emailSent: 'If the address is available, we sent a confirmation link to it.',
    badEmail: 'Enter a valid new e-mail address.',
    needPassword: 'Enter your current password.',
    emailAfterPassword: 'You can change the address after setting a password.',
    twoFactor: 'Two-step sign-in',
    twoFactorOff:
      'Protect the account with a second step: after the password you enter a code from an authenticator app.',
    twoFactorOn: 'On. Backup codes left: {count}.',
    turnOn: 'Turn on two-step sign-in',
    turnOff: 'Turn off',
    unavailable2fa: 'Two-step sign-in is not available on this server.',
    scan: '1. Scan the QR code with an authenticator app or type the key.',
    key: 'Key',
    enterCode: '2. Enter the code from the app and your current password.',
    code: 'App code',
    enable: 'Turn on',
    qr: 'QR code to add HourForge to the authenticator app',
    backup: 'Backup codes',
    backupNote:
      'Store these codes safely. Each works once when you do not have your phone. We will not show them again.',
    copyCodes: 'Copy codes',
    copied: 'Copied',
    savedCodes: 'I saved the codes',
    renew: 'New backup codes',
    renewBody: 'The current backup codes stop working.',
    offBody: 'The password alone will be enough to sign in. Backup codes stop working.',
    twoFactorAfterPassword: 'You can turn on two-step sign-in after setting a password.',
    google: 'Sign-in with Google',
    googleLinked: 'Connected to the Google account {email}.',
    googleOff: 'Connect a Google account to sign in with "Sign in with Google". Your e-mail address stays the same.',
    link: 'Connect Google',
    unlink: 'Disconnect Google',
    linkBody: 'After confirming your password you go to Google and choose the account to connect.',
    goGoogle: 'Go to Google',
    unlinkBody: 'You will no longer sign in with Google. Other devices are signed out.',
    unlinked: 'Google account disconnected.',
    needPasswordFirst: 'You cannot disconnect Google until you set a password.',
    devices: 'Signed-in devices',
    devicesHint: 'Sign out a device you do not recognise. Addresses are shortened and times approximate.',
    thisDevice: 'this device',
    signedIn: 'Signed in {date}',
    lastActive: 'active {date}',
    remembered: 'remembered',
    signOutDevice: 'Sign out',
    signOutLabel: 'Sign out {device}, signed in {date}',
    deviceOut: 'Device signed out.',
    everywhere: 'Sign out of all devices',
    everywhereBody: 'All sessions, this one too, end. A running timer is not stopped.',
    everywhereDo: 'Sign out everywhere',
    logins: 'Sign-in history',
    loginsHint: 'Sign-ins of the last 90 days, failed ones too. If you do not recognise one, change your password.',
    when: 'Time',
    result: 'Result',
    ok: 'Succeeded',
    failed: 'Failed',
    method: 'Method',
    device: 'Device',
    place: 'Place',
    unknown: 'Unknown',
    noLogins: 'No sign-ins recorded.',
    source: 'Places are approximate. Location data: {source}.',
    export: 'Copy of your data',
    exportHint: 'A JSON file with your profile, memberships and all your entries, also from teams you left.',
    download: 'Download my data',
    preparing: 'Preparing…',
    delete: 'Delete account',
    deleteHint:
      'We remove your name, e-mail address and sign-in data. Saved hours stay in team reports as "Deleted user". First transfer or delete the teams you own.',
    deleteBody: 'This cannot be undone. You leave all teams. Enter your password to confirm.',
    deleteNoPassword:
      'This cannot be undone. Your account has no password, so we send a confirmation link to {email}. The account is deleted only after you open it.',
    runningTimer:
      'You have a running timer. It is discarded without saving when the account is deleted. Stop it to keep those hours.',
    deleteDo: 'Delete the account permanently',
    deleting: 'Deleting…',
    sendDelete: 'Send confirmation link',
    deleteSent: 'We sent the link. Open it to delete the account.',
    teamsLink: 'Go to teams',
  },
}

const dateTime = (iso: string, locale: Locale) =>
  new Date(iso).toLocaleString(locale === 'pl' ? 'pl-PL' : 'en-GB', { dateStyle: 'medium', timeStyle: 'short' })

export function AccountLayout() {
  const m = useMessages(messages)
  useScrollToHash(useLocation().hash)
  return (
    <div className="page account">
      <header className="page-head">
        <h1>{m('title')}</h1>
      </header>
      <nav className="tabs" aria-label={m('title')}>
        <NavLink to="/app/account" end>
          <UserRound size={16} aria-hidden="true" />
          {m('profile')}
        </NavLink>
        <NavLink to="/app/account/preferences">
          <Palette size={16} aria-hidden="true" />
          {m('preferences')}
        </NavLink>
        <NavLink to="/app/account/security">
          <ShieldCheck size={16} aria-hidden="true" />
          {m('security')}
        </NavLink>
        <NavLink to="/app/account/data">
          <Database size={16} aria-hidden="true" />
          {m('data')}
        </NavLink>
      </nav>
      <Outlet />
    </div>
  )
}

export function ProfilePage() {
  const user = useUser()
  const m = useMessages(messages)
  const toast = useToast()
  const update = useUpdateProfile()
  const { upload, remove } = useProfilePicture()
  const [name, setName] = useState(user.displayName)
  const [error, setError] = useState('')
  const [editing, setEditing] = useState(false)
  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (!name.trim() || name.trim().length > 255) {
      setError(m('needName'))
      return
    }
    setError('')
    update.mutate(name.trim(), { onSuccess: () => toast(m('saved')) })
  }
  return (
    <div className="account-grid">
      <form className="card stack" onSubmit={submit} noValidate>
        <h2>{m('profile')}</h2>
        <Field label={m('name')} hint={m('nameHint')} error={error || undefined}>
          {props => (
            <input
              {...props}
              className="input"
              value={name}
              maxLength={255}
              autoComplete="name"
              onChange={event => setName(event.target.value)}
            />
          )}
        </Field>
        <div className="field">
          <span className="field-label">{m('email')}</span>
          <p>
            {user.email} ·{' '}
            <Link className="text-link" to="/app/account/security">
              {m('emailChange')}
            </Link>
          </p>
        </div>
        {update.isError && <Notice tone="error">{update.error.message}</Notice>}
        <Button type="submit" variant="primary" pending={update.isPending}>
          {m('save')}
        </Button>
      </form>
      <section className="card stack" aria-labelledby="picture-title">
        <h2 id="picture-title">{m('picture')}</h2>
        {editing ? (
          <PictureEditor
            label={m('newPicture')}
            saving={upload.isPending}
            error={upload.error?.message}
            onSave={picture => upload.mutate(picture, { onSuccess: () => setEditing(false) })}
            onCancel={() => {
              upload.reset()
              setEditing(false)
            }}
          />
        ) : (
          <div className="row-inline">
            <span className="avatar-xl">
              <Avatar user={user} />
            </span>
            <Button
              onClick={() => {
                remove.reset()
                setEditing(true)
              }}
            >
              {m(user.avatarUpdatedAt ? 'changePicture' : 'addPicture')}
            </Button>
            {user.avatarUpdatedAt && (
              <Button variant="ghost" pending={remove.isPending} onClick={() => remove.mutate()}>
                {m('removePicture')}
              </Button>
            )}
          </div>
        )}
        {remove.isError && <Notice tone="error">{remove.error.message}</Notice>}
      </section>
    </div>
  )
}

const hourChoices = [4, 6, 8, 10, 12, 16, 24]

export function PreferencesPage() {
  const user = useUser()
  const { locale, setLocale } = useI18n()
  const m = useMessages(messages)
  const toast = useToast()
  const [theme, setTheme] = useTheme()
  const teams = useTeams(user.id)
  const stored = usePreferences(user.id)
  const save = useSavePreferences(user.id)
  const [draft, setDraft] = useState<Preferences | null>(null)
  const [hint, setHint] = useState('')
  const current = draft ?? stored.data ?? defaultPreferences
  const hours = hourChoices.includes(current.longTimerHours)
    ? hourChoices
    : [...hourChoices, current.longTimerHours].sort((a, b) => a - b)
  const change = (patch: Partial<Preferences>) => {
    setDraft({ ...current, ...patch })
    setHint('')
    if (save.isError) save.reset()
  }
  const toggleNotifications = async (on: boolean) => {
    if (!on) {
      change({ browserNotifications: false })
      return
    }
    if (!('Notification' in window)) {
      setHint(m('noNotifications'))
      return
    }
    const permission =
      Notification.permission === 'default' ? await Notification.requestPermission() : Notification.permission
    if (permission !== 'granted') {
      setHint(m('denied'))
      return
    }
    change({ browserNotifications: true })
  }
  const check = (key: keyof Preferences, label: string, value: boolean, onChange?: (on: boolean) => void) => (
    <label className="check">
      <input
        type="checkbox"
        checked={value}
        onChange={event => (onChange ? onChange(event.target.checked) : change({ [key]: event.target.checked }))}
      />
      {label}
    </label>
  )
  return (
    <div className="stack account-preferences">
      <section className="card stack" aria-labelledby="look-title">
        <h2 id="look-title">{m('look')}</h2>
        <fieldset className="field">
          <legend className="field-label">{m('theme')}</legend>
          <div className="segmented">
            {(['system', 'light', 'dark'] as ThemeChoice[]).map(choice => (
              <button key={choice} type="button" aria-pressed={theme === choice} onClick={() => setTheme(choice)}>
                {m(choice)}
              </button>
            ))}
          </div>
        </fieldset>
        <fieldset className="field">
          <legend className="field-label">{m('language')}</legend>
          <div className="segmented">
            {(['pl', 'en'] as Locale[]).map(choice => (
              <button
                key={choice}
                type="button"
                lang={choice}
                aria-pressed={locale === choice}
                onClick={() => setLocale(choice)}
              >
                {choice === 'pl' ? 'Polski' : 'English'}
              </button>
            ))}
          </div>
        </fieldset>
        <p className="field-hint">{m('localNote')}</p>
      </section>
      {stored.isPending ? (
        <Loading />
      ) : stored.isError ? (
        <LoadError onRetry={() => void stored.refetch()} message={stored.error.message} />
      ) : (
        <form
          className="stack"
          onSubmit={event => {
            event.preventDefault()
            save.mutate(current, {
              onSuccess: () => {
                setDraft(null)
                toast(m('prefsSaved'))
              },
            })
          }}
        >
          <section className="card stack" aria-labelledby="work-title">
            <h2 id="work-title">{m('work')}</h2>
            <div className="form-grid">
              <Field label={m('defaultTeam')} hint={m('defaultTeamHint')}>
                {props => (
                  <select
                    {...props}
                    className="select input"
                    value={current.defaultTeamId ?? ''}
                    onChange={event => change({ defaultTeamId: event.target.value || null })}
                  >
                    <option value="">{m('firstTeam')}</option>
                    {teams.data?.map(team => (
                      <option key={team.id} value={team.id}>
                        {team.name}
                      </option>
                    ))}
                  </select>
                )}
              </Field>
              <Field label={m('start')}>
                {props => (
                  <select
                    {...props}
                    className="select input"
                    value={current.startPage === 'TIME' ? 'DASHBOARD' : current.startPage}
                    onChange={event => change({ startPage: event.target.value as Preferences['startPage'] })}
                  >
                    <option value="DASHBOARD">{m('startWork')}</option>
                    <option value="PROJECTS">{m('startProjects')}</option>
                    <option value="TEAMS">{m('startTeams')}</option>
                  </select>
                )}
              </Field>
              <Field label={m('format')} hint={m('formatHint')}>
                {props => (
                  <select
                    {...props}
                    className="select input"
                    value={current.timeFormat}
                    onChange={event => change({ timeFormat: event.target.value as Preferences['timeFormat'] })}
                  >
                    <option value="CLOCK">{m('clock')}</option>
                    <option value="DECIMAL">{m('decimal')}</option>
                  </select>
                )}
              </Field>
              <Field label={m('weekStart')}>
                {props => (
                  <select
                    {...props}
                    className="select input"
                    value={current.weekStart}
                    onChange={event => change({ weekStart: event.target.value as Preferences['weekStart'] })}
                  >
                    <option value="MONDAY">{m('monday')}</option>
                    <option value="SUNDAY">{m('sunday')}</option>
                  </select>
                )}
              </Field>
            </div>
            {check('titleTimer', m('titleTimer'), current.titleTimer)}
          </section>
          <section className="card stack" id="notifications" aria-labelledby="notifications-title">
            <h2 id="notifications-title">{m('notifications')}</h2>
            <p className="field-hint">{m('notificationsHint')}</p>
            {check('teamEventEmails', m('teamEvents'), current.teamEventEmails ?? true)}
            {check('weeklyReportEmail', m('weekly'), current.weeklyReportEmail ?? true)}
            <h3 className="preferences-subheading">{m('longTimer')}</h3>
            <Field label={m('longTimerAfter')} hint={m('longTimerHint')}>
              {props => (
                <select
                  {...props}
                  className="select input narrow"
                  value={current.longTimerHours}
                  onChange={event => change({ longTimerHours: Number(event.target.value) })}
                >
                  {hours.map(hour => (
                    <option key={hour} value={hour}>
                      {m('hours', { hours: hour })}
                    </option>
                  ))}
                </select>
              )}
            </Field>
            {check('longTimerEmail', m('longTimerEmail'), current.longTimerEmail)}
            {check(
              'browserNotifications',
              m('browserNote'),
              current.browserNotifications,
              on => void toggleNotifications(on),
            )}
            {hint && (
              <p className="field-hint" role="status">
                {hint}
              </p>
            )}
          </section>
          {save.isError && <Notice tone="error">{save.error.message}</Notice>}
          <div className="sticky-save">
            <Button type="submit" variant="primary" pending={save.isPending} disabled={!draft}>
              {m(save.isPending ? 'saving' : 'savePrefs')}
            </Button>
          </div>
        </form>
      )}
    </div>
  )
}

function SetPassword({ user, reason }: { user: User; reason: string }) {
  const m = useMessages(messages)
  const reset = useRequestPasswordReset()
  return (
    <div className="stack-sm">
      <p className="muted">{reason}</p>
      {reset.isSuccess ? (
        <Notice tone="success">{m('setPasswordSent', { email: user.email })}</Notice>
      ) : (
        <div>
          <Button pending={reset.isPending} onClick={() => reset.mutate(user.email)}>
            {m(reset.isPending ? 'sending' : 'setPassword')}
          </Button>
        </div>
      )}
      {reset.isError && <Notice tone="error">{reset.error.message}</Notice>}
    </div>
  )
}

function PasswordConfirm({
  title,
  body,
  action,
  busy,
  error,
  onConfirm,
  onClose,
}: {
  title: string
  body: string
  action: string
  busy: boolean
  error?: string
  onConfirm: (password: string) => void
  onClose: () => void
}) {
  const { t } = useI18n()
  const m = useMessages(messages)
  const [password, setPassword] = useState('')
  return (
    <Dialog open title={title} onClose={onClose} busy={busy}>
      <form
        className="stack"
        onSubmit={event => {
          event.preventDefault()
          if (password) onConfirm(password)
        }}
      >
        <p>{body}</p>
        <Field label={m('currentPassword')}>
          {props => (
            <input
              {...props}
              className="input"
              type="password"
              autoComplete="current-password"
              disabled={busy}
              value={password}
              onChange={event => setPassword(event.target.value)}
            />
          )}
        </Field>
        {error && <Notice tone="error">{error}</Notice>}
        <div className="dialog-actions">
          <Button onClick={onClose} disabled={busy}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" variant="primary" pending={busy} disabled={!password}>
            {action}
          </Button>
        </div>
      </form>
    </Dialog>
  )
}

export function SecurityPage() {
  const user = useUser()
  return (
    <div className="stack">
      <div className="account-grid">
        <PasswordCard user={user} />
        <EmailCard user={user} />
      </div>
      <div className="account-grid">
        <TwoFactorCard user={user} />
        <GoogleCard user={user} />
      </div>
      <DevicesCard />
      <LoginsCard />
    </div>
  )
}

function PasswordCard({ user }: { user: User }) {
  const m = useMessages(messages)
  const change = useChangePassword()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [repeat, setRepeat] = useState('')
  const [error, setError] = useState('')
  const [done, setDone] = useState(false)
  if (user.hasPassword === false)
    return (
      <section className="card stack">
        <h2>{m('password')}</h2>
        <SetPassword user={user} reason={m('noPassword')} />
      </section>
    )
  const submit = (event: FormEvent) => {
    event.preventDefault()
    const message = passwordChangeError(current, next, repeat)
    setError(message)
    setDone(false)
    if (!message)
      change.mutate(
        { currentPassword: current, newPassword: next },
        {
          onSuccess: () => {
            setCurrent('')
            setNext('')
            setRepeat('')
            setDone(true)
          },
        },
      )
  }
  return (
    <form
      className="card stack"
      onSubmit={submit}
      noValidate
      onInput={() => {
        setError('')
        setDone(false)
        if (change.isError) change.reset()
      }}
    >
      <h2>{m('password')}</h2>
      <p className="field-hint">{m('passwordNote')}</p>
      <Field label={m('currentPassword')}>
        {props => (
          <input
            {...props}
            className="input"
            type="password"
            autoComplete="current-password"
            value={current}
            onChange={event => setCurrent(event.target.value)}
          />
        )}
      </Field>
      <Field label={m('newPassword')} hint={m('passwordHint')}>
        {props => (
          <input
            {...props}
            className="input"
            type="password"
            autoComplete="new-password"
            maxLength={72}
            value={next}
            onChange={event => setNext(event.target.value)}
          />
        )}
      </Field>
      <Field label={m('repeat')}>
        {props => (
          <input
            {...props}
            className="input"
            type="password"
            autoComplete="new-password"
            maxLength={72}
            value={repeat}
            onChange={event => setRepeat(event.target.value)}
          />
        )}
      </Field>
      {(error || change.isError) && <Notice tone="error">{error || change.error?.message}</Notice>}
      {done && <Notice tone="success">{m('passwordChanged')}</Notice>}
      <Button type="submit" variant="primary" pending={change.isPending}>
        {m('changePassword')}
      </Button>
    </form>
  )
}

function EmailCard({ user }: { user: User }) {
  const m = useMessages(messages)
  const change = useRequestEmailChange()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  if (user.hasPassword === false)
    return (
      <section className="card stack">
        <h2>{m('emailChange')}</h2>
        <SetPassword user={user} reason={m('emailAfterPassword')} />
      </section>
    )
  const submit = (event: FormEvent) => {
    event.preventDefault()
    const address = email.trim()
    if (!address || address.length > 255 || !address.includes('@')) {
      setError(m('badEmail'))
      return
    }
    if (!password) {
      setError(m('needPassword'))
      return
    }
    setError('')
    change.mutate({ newEmail: address, currentPassword: password }, { onSuccess: () => setPassword('') })
  }
  return (
    <form
      className="card stack"
      onSubmit={submit}
      noValidate
      onInput={() => {
        setError('')
        if (change.isError) change.reset()
      }}
    >
      <h2>{m('emailChange')}</h2>
      <p className="field-hint">{m('emailHint')}</p>
      <Field label={m('newEmail')}>
        {props => (
          <input
            {...props}
            className="input"
            type="email"
            autoComplete="off"
            maxLength={255}
            value={email}
            onChange={event => setEmail(event.target.value)}
          />
        )}
      </Field>
      <Field label={m('currentPassword')}>
        {props => (
          <input
            {...props}
            className="input"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={event => setPassword(event.target.value)}
          />
        )}
      </Field>
      {(error || change.isError) && <Notice tone="error">{error || change.error?.message}</Notice>}
      {change.isSuccess && <Notice tone="success">{m('emailSent')}</Notice>}
      <Button type="submit" variant="primary" pending={change.isPending}>
        {m(change.isPending ? 'sending' : 'sendLink')}
      </Button>
    </form>
  )
}

function TwoFactorCard({ user }: { user: User }) {
  const m = useMessages(messages)
  const { t } = useI18n()
  const status = useTwoFactorStatus(user.id)
  const { setup, enable, disable, renew } = useTwoFactorActions(user.id)
  const [code, setCode] = useState('')
  const [password, setPassword] = useState('')
  const [codes, setCodes] = useState<string[] | null>(null)
  const [dialog, setDialog] = useState<'off' | 'renew' | null>(null)
  const [copied, setCopied] = useState(false)
  if (user.hasPassword === false)
    return (
      <section className="card stack">
        <h2>{m('twoFactor')}</h2>
        <SetPassword user={user} reason={m('twoFactorAfterPassword')} />
      </section>
    )
  return (
    <section className="card stack" aria-labelledby="tf-title">
      <h2 id="tf-title">
        <KeyRound size={18} aria-hidden="true" /> {m('twoFactor')}
      </h2>
      {status.isPending ? (
        <Loading />
      ) : status.isError ? (
        <LoadError onRetry={() => void status.refetch()} message={status.error.message} />
      ) : codes ? (
        <div className="stack" role="region" aria-label={m('backup')}>
          <Notice tone="warning">{m('backupNote')}</Notice>
          <ul className="codes">
            {codes.map(item => (
              <li key={item}>
                <code>{item}</code>
              </li>
            ))}
          </ul>
          <div className="row-inline">
            <Button onClick={() => void navigator.clipboard?.writeText(codes.join('\n')).then(() => setCopied(true))}>
              {m(copied ? 'copied' : 'copyCodes')}
            </Button>
            <Button variant="primary" onClick={() => setCodes(null)}>
              {m('savedCodes')}
            </Button>
          </div>
        </div>
      ) : status.data.enabled ? (
        <>
          <p>
            <span className="ok-text">✓</span> {m('twoFactorOn', { count: status.data.backupCodesLeft })}
          </p>
          <div className="row-inline">
            <Button
              onClick={() => {
                renew.reset()
                setDialog('renew')
              }}
            >
              {m('renew')}
            </Button>
            <Button
              variant="ghost"
              onClick={() => {
                disable.reset()
                setDialog('off')
              }}
            >
              {m('turnOff')}
            </Button>
          </div>
        </>
      ) : !status.data.available ? (
        <p className="muted">{m('unavailable2fa')}</p>
      ) : setup.data ? (
        <form
          className="stack"
          onSubmit={event => {
            event.preventDefault()
            if (code.trim() && password)
              enable.mutate(
                { code, currentPassword: password },
                {
                  onSuccess: backup => {
                    setCodes(backup)
                    setup.reset()
                    setCode('')
                    setPassword('')
                  },
                },
              )
          }}
        >
          <p className="field-hint">{m('scan')}</p>
          <div className="qr-row">
            <QrCode value={setup.data.uri} label={m('qr')} />
            <p className="field-hint">
              {m('key')}: <code className="secret">{setup.data.secret.match(/.{1,4}/g)?.join(' ')}</code>
            </p>
          </div>
          <p className="field-hint">{m('enterCode')}</p>
          <Field label={m('code')}>
            {props => (
              <input
                {...props}
                className="input narrow"
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={8}
                value={code}
                onChange={event => {
                  setCode(event.target.value)
                  if (enable.isError) enable.reset()
                }}
              />
            )}
          </Field>
          <Field label={m('currentPassword')}>
            {props => (
              <input
                {...props}
                className="input"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={event => {
                  setPassword(event.target.value)
                  if (enable.isError) enable.reset()
                }}
              />
            )}
          </Field>
          {enable.isError && <Notice tone="error">{enable.error.message}</Notice>}
          <div className="row-inline">
            <Button
              onClick={() => {
                setup.reset()
                enable.reset()
              }}
              disabled={enable.isPending}
            >
              {t('common.cancel')}
            </Button>
            <Button type="submit" variant="primary" pending={enable.isPending} disabled={!code.trim() || !password}>
              {m('enable')}
            </Button>
          </div>
        </form>
      ) : (
        <>
          <p className="muted">{m('twoFactorOff')}</p>
          <div>
            <Button variant="primary" pending={setup.isPending} onClick={() => setup.mutate()}>
              {m('turnOn')}
            </Button>
          </div>
          {setup.isError && <Notice tone="error">{setup.error.message}</Notice>}
        </>
      )}
      {dialog === 'off' && (
        <PasswordConfirm
          title={m('twoFactor')}
          body={m('offBody')}
          action={m('turnOff')}
          busy={disable.isPending}
          error={disable.error?.message}
          onConfirm={value => disable.mutate(value, { onSuccess: () => setDialog(null) })}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog === 'renew' && (
        <PasswordConfirm
          title={m('renew')}
          body={m('renewBody')}
          action={m('renew')}
          busy={renew.isPending}
          error={renew.error?.message}
          onConfirm={value =>
            renew.mutate(value, {
              onSuccess: backup => {
                setDialog(null)
                setCodes(backup)
              },
            })
          }
          onClose={() => setDialog(null)}
        />
      )}
    </section>
  )
}

function GoogleCard({ user }: { user: User }) {
  const m = useMessages(messages)
  const { locale } = useI18n()
  const google = useGoogleSignIn()
  const start = useStartGoogleLink()
  const unlink = useUnlinkGoogle()
  const toast = useToast()
  const [search, setSearch] = useSearchParams()
  const [dialog, setDialog] = useState<'link' | 'unlink' | null>(null)
  const outcome = googleOutcome(search.get('google'), 'link')
  const linked = user.google?.linked === true
  const hasPassword = user.hasPassword !== false
  if (!google && !linked) return null
  const clear = () => {
    if (search.has('google'))
      setSearch(
        previous => {
          const next = new URLSearchParams(previous)
          next.delete('google')
          return next
        },
        { replace: true },
      )
  }
  return (
    <section className="card stack" id="google" aria-labelledby="google-title">
      <h2 id="google-title">{m('google')}</h2>
      {outcome && <Notice tone={outcome.tone === 'success' ? 'success' : 'error'}>{outcome.text}</Notice>}
      {linked ? (
        <>
          <p>
            {m('googleLinked', { email: user.google?.email ?? '' })}
            {user.google?.linkedAt ? ` (${dateTime(user.google.linkedAt, locale)})` : ''}
          </p>
          {hasPassword ? (
            <div>
              <Button
                onClick={() => {
                  clear()
                  unlink.reset()
                  setDialog('unlink')
                }}
              >
                {m('unlink')}
              </Button>
            </div>
          ) : (
            <p className="field-hint">{m('needPasswordFirst')}</p>
          )}
        </>
      ) : (
        <>
          <p className="muted">{m('googleOff')}</p>
          <div>
            <Button
              pending={start.isPending && !dialog}
              onClick={() => {
                clear()
                start.reset()
                if (hasPassword) setDialog('link')
                else start.mutate(undefined)
              }}
            >
              {m('link')}
            </Button>
          </div>
          {!dialog && start.isError && <Notice tone="error">{start.error.message}</Notice>}
        </>
      )}
      {dialog === 'link' && (
        <PasswordConfirm
          title={m('link')}
          body={m('linkBody')}
          action={m('goGoogle')}
          busy={start.isPending}
          error={start.error?.message}
          onConfirm={value => start.mutate(value)}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog === 'unlink' && (
        <PasswordConfirm
          title={m('unlink')}
          body={m('unlinkBody')}
          action={m('unlink')}
          busy={unlink.isPending}
          error={unlink.error?.message}
          onConfirm={value =>
            unlink.mutate(value, {
              onSuccess: () => {
                setDialog(null)
                toast(m('unlinked'))
              },
            })
          }
          onClose={() => setDialog(null)}
        />
      )}
    </section>
  )
}

function DevicesCard() {
  const user = useUser()
  const { t, locale } = useI18n()
  const m = useMessages(messages)
  const toast = useToast()
  const sessions = useSessions(user.id)
  const end = useEndSession(user.id)
  const everywhere = useSignOutEverywhere()
  const [confirm, setConfirm] = useState(false)
  return (
    <section className="card stack" id="sessions" aria-labelledby="devices-title">
      <h2 id="devices-title">{m('devices')}</h2>
      <p className="field-hint">{m('devicesHint')}</p>
      {sessions.isPending ? (
        <Loading />
      ) : sessions.isError ? (
        <LoadError onRetry={() => void sessions.refetch()} message={sessions.error.message} />
      ) : (
        <ul className="list-card bare">
          {sessions.data.map((session, index) => (
            <li key={session.id ?? `${session.signedInAt}-${index}`}>
              <span className="stack-xs list-main">
                <b>
                  {deviceLabel(session)}
                  {session.current && <span className="badge badge-ok">{m('thisDevice')}</span>}
                </b>
                <small>
                  {m('signedIn', { date: dateTime(session.signedInAt, locale) })} ·{' '}
                  {m('lastActive', { date: dateTime(session.lastActiveAt, locale) })}
                  {session.location ? ` · ${session.location}` : ''}
                  {session.remembered ? ` · ${m('remembered')}` : ''}
                </small>
              </span>
              {!session.current && (
                <Button
                  small
                  disabled={end.isPending}
                  aria-label={m('signOutLabel', {
                    device: deviceLabel(session),
                    date: dateTime(session.signedInAt, locale),
                  })}
                  onClick={() => end.mutate(session.id, { onSuccess: () => toast(m('deviceOut')) })}
                >
                  {m('signOutDevice')}
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
      {end.isError && <Notice tone="error">{end.error.message}</Notice>}
      <div>
        <Button
          variant="ghost"
          onClick={() => {
            everywhere.reset()
            setConfirm(true)
          }}
        >
          {m('everywhere')}
        </Button>
      </div>
      {confirm && (
        <Dialog open title={m('everywhere')} onClose={() => setConfirm(false)} busy={everywhere.isPending}>
          <p>{m('everywhereBody')}</p>
          {everywhere.isError && <Notice tone="error">{everywhere.error.message}</Notice>}
          <div className="dialog-actions">
            <Button onClick={() => setConfirm(false)} disabled={everywhere.isPending}>
              {t('common.cancel')}
            </Button>
            <Button variant="danger" pending={everywhere.isPending} onClick={() => everywhere.mutate()}>
              {m('everywhereDo')}
            </Button>
          </div>
        </Dialog>
      )}
    </section>
  )
}

function LoginsCard() {
  const user = useUser()
  const { locale } = useI18n()
  const m = useMessages(messages)
  const history = useLoginHistory(user.id)
  const methods = history.data?.entries.some(entry => entry.method) === true
  return (
    <section className="card stack" aria-labelledby="logins-title">
      <h2 id="logins-title">{m('logins')}</h2>
      <p className="field-hint">{m('loginsHint')}</p>
      {history.isPending ? (
        <Loading />
      ) : history.isError ? (
        <LoadError onRetry={() => void history.refetch()} message={history.error.message} />
      ) : !history.data.entries.length ? (
        <p className="muted">{m('noLogins')}</p>
      ) : (
        <>
          <div className="table-scroll login-table" role="region" aria-labelledby="logins-title" tabIndex={0}>
            <table className="table">
              <thead>
                <tr>
                  <th scope="col">{m('when')}</th>
                  <th scope="col">{m('result')}</th>
                  {methods && <th scope="col">{m('method')}</th>}
                  <th scope="col">{m('device')}</th>
                  <th scope="col">{m('place')}</th>
                </tr>
              </thead>
              <tbody>
                {history.data.entries.map((entry, index) => (
                  <tr key={`${entry.occurredAt}-${index}`}>
                    <td>{dateTime(entry.occurredAt, locale)}</td>
                    <td>
                      {entry.successful ? (
                        <span className="badge badge-ok">{m('ok')}</span>
                      ) : (
                        <span className="badge badge-bad">{m('failed')}</span>
                      )}
                    </td>
                    {methods && <td>{entry.method === 'GOOGLE' ? 'Google' : entry.method ? m('password') : '–'}</td>}
                    <td>{deviceLabel(entry)}</td>
                    <td>{entry.location || m('unknown')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <ul className="login-list">
            {history.data.entries.map((entry, index) => (
              <li key={entry.occurredAt + index}>
                <div className="row-inline">
                  <time dateTime={entry.occurredAt}>{dateTime(entry.occurredAt, locale)}</time>
                  <span className={entry.successful ? 'badge badge-ok' : 'badge badge-bad'}>
                    {m(entry.successful ? 'ok' : 'failed')}
                  </span>
                </div>
                <b>{deviceLabel(entry)}</b>
                <small>
                  {entry.location || m('unknown')}
                  {entry.method && <> · {entry.method === 'GOOGLE' ? 'Google' : m('password')}</>}
                </small>
              </li>
            ))}
          </ul>
          {history.data.locationSource && (
            <p className="field-hint">{m('source', { source: history.data.locationSource })}</p>
          )}
        </>
      )}
    </section>
  )
}

export function DataPage() {
  const user = useUser()
  const { t } = useI18n()
  const m = useMessages(messages)
  const download = useDownloadAccountData()
  const remove = useDeleteAccount(user.id)
  const byEmail = useRequestAccountDeletionByEmail()
  const timer = useActiveTimer(user.id).data
  const [open, setOpen] = useState(false)
  const [password, setPassword] = useState('')
  const closeRef = useRef<HTMLButtonElement>(null)
  useEffect(() => {
    if (byEmail.isSuccess) closeRef.current?.focus()
  }, [byEmail.isSuccess])
  const noPassword = user.hasPassword === false
  const failure = remove.error ?? byEmail.error
  return (
    <div className="stack">
      <section className="card stack" aria-labelledby="export-title">
        <h2 id="export-title">{m('export')}</h2>
        <p className="muted">{m('exportHint')}</p>
        <div>
          <Button pending={download.isPending} onClick={() => download.mutate()}>
            {m(download.isPending ? 'preparing' : 'download')}
          </Button>
        </div>
        {download.isError && <Notice tone="error">{download.error.message}</Notice>}
      </section>
      <section className="card stack danger-zone" aria-labelledby="delete-title">
        <h2 id="delete-title">{m('delete')}</h2>
        <p className="muted">{m('deleteHint')}</p>
        <div>
          <Button
            variant="ghost"
            className="danger-text"
            onClick={() => {
              remove.reset()
              byEmail.reset()
              setPassword('')
              setOpen(true)
            }}
          >
            {m('delete')}
          </Button>
        </div>
      </section>
      {open && (
        <Dialog open title={m('delete')} onClose={() => setOpen(false)} busy={remove.isPending || byEmail.isPending}>
          <form
            className="stack"
            onSubmit={event => {
              event.preventDefault()
              if (noPassword) byEmail.mutate()
              else if (password) remove.mutate(password)
            }}
          >
            <p>{noPassword ? m('deleteNoPassword', { email: user.email }) : m('deleteBody')}</p>
            {timer && <Notice tone="warning">{m('runningTimer')}</Notice>}
            {!noPassword && (
              <Field label={m('currentPassword')}>
                {props => (
                  <input
                    {...props}
                    className="input"
                    type="password"
                    autoComplete="current-password"
                    disabled={remove.isPending}
                    value={password}
                    onChange={event => setPassword(event.target.value)}
                  />
                )}
              </Field>
            )}
            {failure && (
              <Notice
                tone="error"
                action={
                  failure.message.includes('zespo') ? (
                    <Link className="action-link" to="/app/teams">
                      {m('teamsLink')}
                    </Link>
                  ) : undefined
                }
              >
                {failure.message}
              </Notice>
            )}
            {byEmail.isSuccess && <Notice tone="success">{m('deleteSent')}</Notice>}
            <div className="dialog-actions">
              <button
                ref={closeRef}
                type="button"
                className="btn"
                onClick={() => setOpen(false)}
                disabled={remove.isPending || byEmail.isPending}
              >
                {t(byEmail.isSuccess ? 'common.close' : 'common.cancel')}
              </button>
              {!byEmail.isSuccess && (
                <Button
                  type="submit"
                  variant="danger"
                  pending={remove.isPending || byEmail.isPending}
                  disabled={!noPassword && !password}
                >
                  {noPassword ? m('sendDelete') : m(remove.isPending ? 'deleting' : 'deleteDo')}
                </Button>
              )}
            </div>
          </form>
        </Dialog>
      )}
    </div>
  )
}
