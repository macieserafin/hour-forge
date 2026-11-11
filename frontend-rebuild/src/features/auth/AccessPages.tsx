import { useEffect, useRef, useState, type FormEvent } from 'react'
import { ArrowRight, Eye, EyeOff, LockKeyhole, Mail, UserRound, Users } from 'lucide-react'
import { useMutation } from '@tanstack/react-query'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useI18n, useMessages } from '../../content/i18n'
import { ApiError, unsubscribe, type InvitationStatus, type UnsubscribeKind, type User } from '../../lib/api'
import { useConfirmAccountDeletion } from '../../lib/account'
import { useConfirmEmail, useConfirmEmailChange, useConfirmPasswordReset, useRegister, useRequestPasswordReset, useSession } from '../../lib/auth'
import { googleSignInUrl, HOME, loginUrl, safeReturnTo } from '../../lib/navigation'
import { usePublicConfig } from '../../lib/operator'
import { registrationError } from '../../lib/registration'
import { useAcceptInvitation, useInvitationPreview } from '../../lib/teams'
import { Button, Field, Loading, Notice } from '../../ui'
import { BotCheckWidget } from '../../ui/BotCheck'
import { AuthLayout } from './AuthLayout'
import { GoogleButton } from './GoogleButton'

const messages = {
  pl: {
    'register.title': 'Utwórz konto',
    'register.intro': 'Mierz czas sam albo z zespołem. Konto jest bezpłatne.',
    'register.introInvite': 'Załóż konto, aby po zalogowaniu wrócić do zaproszenia i dołączyć do zespołu.',
    'register.name': 'Nazwa wyświetlana',
    'register.passwordHint': 'Co najmniej 12 znaków. Możesz używać spacji i znaków specjalnych.',
    'register.confirm': 'Powtórz hasło',
    'register.submit': 'Utwórz konto',
    'register.submitting': 'Tworzenie konta…',
    'register.botWait': 'Poczekaj, aż weryfikacja pod formularzem się zakończy.',
    'register.botFailed': 'Nie udało się wczytać weryfikacji. Sprawdź połączenie lub blokadę skryptów i odśwież stronę.',
    'register.haveAccount': 'Masz już konto?',
    'register.signIn': 'Zaloguj się',
    'forgot.title': 'Nie pamiętasz hasła?',
    'forgot.intro': 'Podaj adres e-mail konta. Wyślemy link do ustawienia nowego hasła.',
    'forgot.sent': 'Jeśli konto o takim adresie istnieje, wysłaliśmy na nie link do ustawienia nowego hasła. Link jest ważny przez godzinę.',
    'forgot.submit': 'Wyślij link',
    'forgot.sending': 'Wysyłanie…',
    'back.login': 'Wróć do logowania',
    'go.login': 'Przejdź do logowania',
    'reset.title': 'Ustaw nowe hasło',
    'reset.password': 'Nowe hasło',
    'reset.confirm': 'Powtórz nowe hasło',
    'reset.submit': 'Ustaw hasło',
    'reset.saving': 'Zapisywanie…',
    'reset.done': 'Hasło zostało zmienione. Wszystkie urządzenia zostały wylogowane.',
    'reset.newLink': 'Poproś o nowy link',
    'verify.title': 'Potwierdzenie adresu e-mail',
    'verify.pending': 'Potwierdzanie adresu…',
    'verify.done': 'Adres e-mail został potwierdzony. Możesz się zalogować.',
    'verify.failedHint': 'Zaloguj się, aby poprosić o nowy link.',
    'change.title': 'Zmiana adresu e-mail',
    'change.pending': 'Potwierdzanie zmiany…',
    'change.done': 'Adres e-mail został zmieniony. Zaloguj się nowym adresem.',
    'delete.title': 'Usunięcie konta',
    'delete.body': 'Usuniemy Twoje imię, adres e-mail i dane logowania. Zapisane godziny pozostaną w raportach zespołów pod nazwą „Użytkownik usunięty”. Trwający timer zostanie odrzucony bez zapisania czasu. Tej operacji nie można cofnąć. Jeśli nie chcesz usuwać konta, zamknij tę stronę.',
    'delete.otherAccount': 'Ta przeglądarka jest zalogowana na konto {email}. Link usuwa konto, na które wysłaliśmy wiadomość.',
    'delete.stillSignedIn': 'Ta przeglądarka pozostaje zalogowana na konto {email}, bo link dotyczył innego konta.',
    'delete.submit': 'Usuń konto na stałe',
    'delete.deleting': 'Usuwanie…',
    'delete.done': 'Konto zostało usunięte. Wszystkie urządzenia tego konta zostały wylogowane.',
    'delete.teams': 'Przejdź do zespołów',
    'delete.settings': 'Przejdź do ustawień konta',
    'unsub.title': 'Powiadomienia e-mail',
    'unsub.incomplete': 'Ten link jest niekompletny. Ustawienia powiadomień znajdziesz w koncie.',
    'unsub.pending': 'Zapisywanie ustawienia…',
    'unsub.done': 'Nie będziemy już wysyłać {kind}.',
    'unsub.settings': 'Ustawienia powiadomień w koncie',
    'unsub.TEAM_EVENTS': 'wiadomości o zmianach w zespołach',
    'unsub.WEEKLY_REPORT': 'cotygodniowych raportów zespołu',
    'unsub.LONG_TIMER': 'przypomnień o zapomnianym timerze',
    'invite.title': 'Zaproszenie do zespołu',
    'invite.joined': 'Masz dostęp do zespołu',
    'invite.checking': 'Sprawdzanie zaproszenia…',
    'invite.as': 'Zalogowano jako {email}.',
    'invite.body': 'Zespół {team} zaprasza Cię do współpracy. Po dołączeniu zobaczysz jego projekty i zapiszesz w nich czas. Osoby zarządzające zespołem zobaczą Twoją pracę w tym zespole.',
    'invite.valid': 'Zaproszenie jest ważne do {date}.',
    'invite.accept': 'Dołącz do zespołu',
    'invite.accepting': 'Dołączanie…',
    'invite.welcome': 'Należysz teraz do zespołu {team}. Możesz od razu zapisać w nim czas.',
    'invite.already': 'Należysz już do zespołu {team}.',
    'invite.open': 'Przejdź do swojej pracy',
    'invite.back': 'Wróć do swojej pracy',
    'invite.NEW_INVITATION_REQUIRED': 'Po usunięciu z zespołu {team} potrzebujesz zaproszenia utworzonego później. Poproś właściciela o nowy link.',
    'invite.EXPIRED': 'To zaproszenie do zespołu {team} wygasło. Poproś właściciela o nowy link.',
    'invite.REVOKED': 'To zaproszenie do zespołu {team} zostało unieważnione. Poproś właściciela o nowy link.',
    'invite.EXHAUSTED': 'Limit użyć zaproszenia do zespołu {team} został wyczerpany. Poproś właściciela o nowy link.',
    'invite.TEAM_ARCHIVED': 'Zespół {team} został zarchiwizowany i nie przyjmuje nowych członków.',
  },
  en: {
    'register.title': 'Create an account',
    'register.intro': 'Track time on your own or with a team. The account is free.',
    'register.introInvite': 'Create an account to return to your invitation after signing in and join the team.',
    'register.name': 'Display name',
    'register.passwordHint': 'At least 12 characters. Spaces and special characters are fine.',
    'register.confirm': 'Repeat the password',
    'register.submit': 'Create account',
    'register.submitting': 'Creating the account…',
    'register.botWait': 'Wait until the check below the form finishes.',
    'register.botFailed': 'The check could not be loaded. Check your connection or script blocker and reload the page.',
    'register.haveAccount': 'Already have an account?',
    'register.signIn': 'Sign in',
    'forgot.title': 'Forgot your password?',
    'forgot.intro': 'Enter the e-mail address of your account. We will send a link to set a new password.',
    'forgot.sent': 'If an account with this address exists, we sent it a link to set a new password. The link is valid for one hour.',
    'forgot.submit': 'Send the link',
    'forgot.sending': 'Sending…',
    'back.login': 'Back to sign-in',
    'go.login': 'Go to sign-in',
    'reset.title': 'Set a new password',
    'reset.password': 'New password',
    'reset.confirm': 'Repeat the new password',
    'reset.submit': 'Set the password',
    'reset.saving': 'Saving…',
    'reset.done': 'The password was changed. All devices were signed out.',
    'reset.newLink': 'Ask for a new link',
    'verify.title': 'E-mail confirmation',
    'verify.pending': 'Confirming the address…',
    'verify.done': 'The e-mail address is confirmed. You can sign in.',
    'verify.failedHint': 'Sign in to ask for a new link.',
    'change.title': 'E-mail change',
    'change.pending': 'Confirming the change…',
    'change.done': 'The e-mail address was changed. Sign in with the new address.',
    'delete.title': 'Account deletion',
    'delete.body': 'We will remove your name, e-mail address and sign-in data. Saved hours stay in team reports as "Deleted user". A running timer is discarded without saving its time. This cannot be undone. If you do not want to delete the account, close this page.',
    'delete.otherAccount': 'This browser is signed in as {email}. The link deletes the account we sent the message to.',
    'delete.stillSignedIn': 'This browser stays signed in as {email}, because the link was for another account.',
    'delete.submit': 'Delete the account permanently',
    'delete.deleting': 'Deleting…',
    'delete.done': 'The account was deleted. All its devices were signed out.',
    'delete.teams': 'Go to teams',
    'delete.settings': 'Go to account settings',
    'unsub.title': 'E-mail notifications',
    'unsub.incomplete': 'This link is incomplete. Notification settings are in your account.',
    'unsub.pending': 'Saving the setting…',
    'unsub.done': 'We will no longer send {kind}.',
    'unsub.settings': 'Notification settings in your account',
    'unsub.TEAM_EVENTS': 'messages about changes in teams',
    'unsub.WEEKLY_REPORT': 'weekly team reports',
    'unsub.LONG_TIMER': 'reminders about a forgotten timer',
    'invite.title': 'Team invitation',
    'invite.joined': 'You have access to the team',
    'invite.checking': 'Checking the invitation…',
    'invite.as': 'Signed in as {email}.',
    'invite.body': '{team} invites you to work together. After joining you will see its projects and can record time in them. The people managing the team will see your work in this team.',
    'invite.valid': 'The invitation is valid until {date}.',
    'invite.accept': 'Join the team',
    'invite.accepting': 'Joining…',
    'invite.welcome': 'You are now a member of {team}. You can record time there right away.',
    'invite.already': 'You are already a member of {team}.',
    'invite.open': 'Go to your work',
    'invite.back': 'Back to your work',
    'invite.NEW_INVITATION_REQUIRED': 'After being removed from {team} you need an invitation created later. Ask the owner for a new link.',
    'invite.EXPIRED': 'This invitation to {team} has expired. Ask the owner for a new link.',
    'invite.REVOKED': 'This invitation to {team} was revoked. Ask the owner for a new link.',
    'invite.EXHAUSTED': 'This invitation to {team} has been used up. Ask the owner for a new link.',
    'invite.TEAM_ARCHIVED': '{team} is archived and does not accept new members.',
  },
}

function PasswordInput({ props, value, onChange, show, autoComplete, onToggle }: { props: object; value: string; onChange: (value: string) => void; show: boolean; autoComplete: string; onToggle?: () => void }) {
  const { t } = useI18n()
  return <div className="input-wrap"><LockKeyhole size={17} aria-hidden="true" />
    <input {...props} className="input" type={show ? 'text' : 'password'} autoComplete={autoComplete} maxLength={72} required value={value} onChange={event => onChange(event.target.value)} />
    {onToggle && <button type="button" className="icon-btn password-toggle" aria-label={t(show ? 'auth.hidePassword' : 'auth.showPassword')} aria-pressed={show} onClick={onToggle}>
      {show ? <EyeOff size={17} aria-hidden="true" /> : <Eye size={17} aria-hidden="true" />}
    </button>}
  </div>
}

/** U02 registration; keeps an invitation in returnTo through the confirmation and sign-in. */
export function RegisterPage() {
  const m = useMessages(messages)
  const { t, locale } = useI18n()
  const [displayName, setDisplayName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [show, setShow] = useState(false)
  const [validation, setValidation] = useState('')
  const [token, setToken] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)
  const registration = useRegister()
  const config = usePublicConfig()
  const siteKey = config.data?.botCheckSiteKey ?? null
  const navigate = useNavigate()
  const [search] = useSearchParams()
  const returnTo = safeReturnTo(search.get('returnTo'))
  const error = validation || registration.error?.message

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const input = { displayName, email, password }
    const message = registrationError(input, confirmation) || (siteKey && !token ? m('register.botWait') : '')
    setValidation(message)
    if (message) return
    try {
      await registration.mutateAsync({ ...input, botCheckToken: token })
      setPassword(''); setConfirmation('')
      navigate(loginUrl(returnTo), { replace: true, state: { registered: true, email: email.trim() } })
    } catch {
      // A used check token does not work twice; a new widget appears for the next attempt.
      if (siteKey) setAttempt(value => value + 1)
    }
  }

  return <AuthLayout>
    <div><h1>{m('register.title')}</h1><p className="muted">{m(returnTo.startsWith('/invite/') ? 'register.introInvite' : 'register.intro')}</p></div>
    <form aria-label={m('register.title')} onSubmit={submit} onInput={() => { setValidation(''); if (registration.isError) registration.reset() }}>
      <Field label={m('register.name')}>{props => <div className="input-wrap"><UserRound size={17} aria-hidden="true" />
        <input {...props} className="input" name="displayName" autoComplete="name" maxLength={255} required value={displayName} disabled={registration.isPending} onChange={event => setDisplayName(event.target.value)} /></div>}</Field>
      <Field label={t('auth.email')}>{props => <div className="input-wrap"><Mail size={17} aria-hidden="true" />
        <input {...props} className="input" name="email" type="email" autoComplete="username" maxLength={255} required value={email} disabled={registration.isPending} onChange={event => setEmail(event.target.value)} /></div>}</Field>
      <Field label={t('auth.password')} hint={m('register.passwordHint')}>{props =>
        <PasswordInput props={props} value={password} onChange={setPassword} show={show} autoComplete="new-password" onToggle={() => setShow(value => !value)} />}</Field>
      <Field label={m('register.confirm')}>{props => <PasswordInput props={props} value={confirmation} onChange={setConfirmation} show={show} autoComplete="new-password" />}</Field>
      {siteKey && <BotCheckWidget siteKey={siteKey} attempt={attempt} onToken={setToken} language={locale} failedText={m('register.botFailed')} />}
      {error && <Notice tone="error">{error}</Notice>}
      <Button type="submit" variant="primary" wide pending={registration.isPending}>{registration.isPending ? m('register.submitting') : <>{m('register.submit')}<ArrowRight size={17} aria-hidden="true" /></>}</Button>
    </form>
    {config.data?.googleSignIn && <GoogleButton href={googleSignInUrl({ returnTo: search.get('returnTo') })} />}
    <p className="auth-alt">{m('register.haveAccount')} <Link className="text-link" to={loginUrl(returnTo)}>{m('register.signIn')}</Link></p>
  </AuthLayout>
}

export function ForgotPasswordPage() {
  const m = useMessages(messages)
  const { t } = useI18n()
  const request = useRequestPasswordReset()
  const [email, setEmail] = useState('')
  return <AuthLayout>
    <h1>{m('forgot.title')}</h1>
    {request.isSuccess ? <Notice tone="success">{m('forgot.sent')}</Notice> : <>
      <p className="muted">{m('forgot.intro')}</p>
      <form onSubmit={event => { event.preventDefault(); request.mutate(email) }} aria-label={m('forgot.title')}>
        <Field label={t('auth.email')} error={request.isError ? request.error.message : undefined}>{props => <div className="input-wrap"><Mail size={17} aria-hidden="true" />
          <input {...props} className="input" type="email" autoComplete="username" maxLength={255} required value={email} disabled={request.isPending}
            onChange={event => { setEmail(event.target.value); if (request.isError) request.reset() }} /></div>}</Field>
        <Button type="submit" variant="primary" wide pending={request.isPending}>{m(request.isPending ? 'forgot.sending' : 'forgot.submit')}</Button>
      </form></>}
    <p className="auth-alt"><Link className="text-link" to="/login">{m('back.login')}</Link></p>
  </AuthLayout>
}

export function ResetPasswordPage() {
  const m = useMessages(messages)
  const { token = '' } = useParams()
  const reset = useConfirmPasswordReset()
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [validation, setValidation] = useState('')
  const submit = (event: FormEvent) => {
    event.preventDefault()
    // Only the password rules apply here; name and address of the sign-up check get valid placeholders.
    const message = registrationError({ displayName: 'x', email: 'x', password }, confirmation)
    setValidation(message)
    if (!message) reset.mutate({ token, newPassword: password }, { onSuccess: () => { setPassword(''); setConfirmation('') } })
  }
  return <AuthLayout>
    <h1>{m('reset.title')}</h1>
    {reset.isSuccess ? <><Notice tone="success">{m('reset.done')}</Notice><Link className="btn btn-primary btn-wide" to="/login">{m('go.login')}</Link></>
      : <form onSubmit={submit} aria-label={m('reset.title')} onInput={() => { setValidation(''); if (reset.isError) reset.reset() }}>
        <Field label={m('reset.password')} hint={m('register.passwordHint')}>{props => <PasswordInput props={props} value={password} onChange={setPassword} show={false} autoComplete="new-password" />}</Field>
        <Field label={m('reset.confirm')}>{props => <PasswordInput props={props} value={confirmation} onChange={setConfirmation} show={false} autoComplete="new-password" />}</Field>
        {(validation || reset.isError) && <Notice tone="error">{validation || reset.error?.message}</Notice>}
        <Button type="submit" variant="primary" wide pending={reset.isPending}>{m(reset.isPending ? 'reset.saving' : 'reset.submit')}</Button>
        <p className="auth-alt"><Link className="text-link" to="/forgot-password">{m('reset.newLink')}</Link></p>
      </form>}
  </AuthLayout>
}

/** Redeems a single-use link once, even when effects run twice in development. */
function useOnce(run: () => void) {
  const started = useRef(false)
  useEffect(() => { if (!started.current) { started.current = true; run() } }, [run])
}

export function VerifyEmailPage() {
  const m = useMessages(messages)
  const { token = '' } = useParams()
  const confirm = useConfirmEmail()
  useOnce(() => confirm.mutate(token))
  return <AuthLayout>
    <h1>{m('verify.title')}</h1>
    {confirm.isError ? <Notice tone="error">{confirm.error.message} {m('verify.failedHint')}</Notice>
      : confirm.isSuccess ? <Notice tone="success">{m('verify.done')}</Notice> : <Loading label={m('verify.pending')} />}
    <Link className="btn btn-primary btn-wide" to="/login">{m('go.login')}</Link>
  </AuthLayout>
}

export function ConfirmEmailChangePage() {
  const m = useMessages(messages)
  const { token = '' } = useParams()
  const confirm = useConfirmEmailChange()
  useOnce(() => confirm.mutate(token))
  return <AuthLayout>
    <h1>{m('change.title')}</h1>
    {confirm.isError ? <Notice tone="error">{confirm.error.message}</Notice> : confirm.isSuccess ? <Notice tone="success">{m('change.done')}</Notice> : <Loading label={m('change.pending')} />}
    <Link className="btn btn-wide" to="/login">{m('go.login')}</Link>
  </AuthLayout>
}

/** U22: deletion by e-mailed link. Mail scanners open links themselves, so nothing happens before the button is pressed. */
export function ConfirmAccountDeletionPage() {
  const m = useMessages(messages)
  const { token = '' } = useParams()
  const confirm = useConfirmAccountDeletion()
  const signedIn = useSession().data
  const result = useRef<HTMLDivElement>(null)
  const status = confirm.error instanceof ApiError ? confirm.error.status : null
  const linkDead = status === 400
  useEffect(() => { if (confirm.isSuccess || confirm.isError) result.current?.focus() }, [confirm.isSuccess, confirm.isError, confirm.error])
  return <AuthLayout>
    <h1>{m('delete.title')}</h1>
    <div tabIndex={-1} ref={result} className="stack">
      {confirm.isSuccess ? <>
        <Notice tone="success">{m('delete.done')}</Notice>
        {signedIn && <p className="muted">{m('delete.stillSignedIn', { email: signedIn.email })}</p>}
        <Link className="btn btn-wide" to="/login">{m('go.login')}</Link>
      </> : <>
        {signedIn && !linkDead && <Notice tone="info">{m('delete.otherAccount', { email: signedIn.email })}</Notice>}
        {!linkDead && <p>{m('delete.body')}</p>}
        {confirm.isError && <Notice tone="error" action={status === 409 ? <Link className="action-link" to="/app/teams">{m('delete.teams')}</Link> : undefined}>{confirm.error.message}</Notice>}
        {!linkDead && <Button variant="danger" wide pending={confirm.isPending} onClick={() => confirm.mutate(token)}>{m(confirm.isPending ? 'delete.deleting' : 'delete.submit')}</Button>}
        <p className="auth-alt">{linkDead ? <Link className="text-link" to="/app/account">{m('delete.settings')}</Link> : <Link className="text-link" to="/login">{m('go.login')}</Link>}</p>
      </>}
    </div>
  </AuthLayout>
}

export function UnsubscribePage() {
  const m = useMessages(messages)
  const { token = '' } = useParams()
  const [search] = useSearchParams()
  const raw = search.get('kind')
  const kind = raw === 'TEAM_EVENTS' || raw === 'WEEKLY_REPORT' || raw === 'LONG_TIMER' ? raw as UnsubscribeKind : null
  const done = useMutation({ mutationFn: unsubscribe, retry: false, gcTime: 0 })
  useOnce(() => { if (kind) done.mutate({ token, kind }) })
  return <AuthLayout>
    <h1>{m('unsub.title')}</h1>
    {!kind ? <Notice tone="error">{m('unsub.incomplete')}</Notice>
      : done.isError ? <Notice tone="error">{done.error.message}</Notice>
      : done.isSuccess ? <Notice tone="success">{m('unsub.done', { kind: m(`unsub.${kind}`) })}</Notice> : <Loading label={m('unsub.pending')} />}
    <Link className="btn btn-wide" to="/app/account/notifications">{m('unsub.settings')}</Link>
  </AuthLayout>
}

/** U04/F02: invitation preview and joining, after sign-in or registration that kept the link. */
export function InvitePage({ user }: { user: User }) {
  const m = useMessages(messages)
  const { locale } = useI18n()
  const { token = '' } = useParams()
  const preview = useInvitationPreview(user.id, token)
  const accept = useAcceptInvitation(user.id)
  const invitation = preview.data
  const status = invitation?.status as InvitationStatus | undefined
  return <AuthLayout>
    <Users size={28} aria-hidden="true" />
    <h1>{m(accept.data ? 'invite.joined' : 'invite.title')}</h1>
    {accept.data ? <><Notice tone="success">{m('invite.welcome', { team: accept.data.name })}</Notice>
      <Link className="btn btn-primary btn-wide" to={`/app/work?team=${encodeURIComponent(accept.data.id)}`}>{m('invite.open')}<ArrowRight size={16} aria-hidden="true" /></Link></>
      : preview.isPending ? <Loading label={m('invite.checking')} />
      : preview.isError ? <Notice tone="error">{preview.error.message}</Notice>
      : status === 'ALREADY_MEMBER' ? <><p>{m('invite.already', { team: invitation!.teamName })}</p><Link className="btn btn-primary btn-wide" to={`/app/work?team=${encodeURIComponent(invitation!.teamId)}`}>{m('invite.open')}</Link></>
      : status && status !== 'ACTIVE' ? <Notice tone="error">{m(`invite.${status}`, { team: invitation!.teamName })}</Notice>
      : <>
        <p className="muted">{m('invite.as', { email: user.email })}</p>
        <p>{m('invite.body', { team: invitation!.teamName })}</p>
        <p className="muted">{m('invite.valid', { date: new Date(invitation!.expiresAt).toLocaleString(locale === 'pl' ? 'pl-PL' : 'en-GB', { dateStyle: 'medium', timeStyle: 'short' }) })}</p>
        {accept.isError && <Notice tone="error">{accept.error.message}</Notice>}
        <Button variant="primary" wide pending={accept.isPending} onClick={() => accept.mutate(token)}>{m(accept.isPending ? 'invite.accepting' : 'invite.accept')}</Button>
      </>}
    <p className="auth-alt"><Link className="text-link" to={HOME}>{m('invite.back')}</Link></p>
  </AuthLayout>
}
