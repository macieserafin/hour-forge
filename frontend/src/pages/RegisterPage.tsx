import { useState, type FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ArrowRight, Eye, EyeOff, LoaderCircle, LockKeyhole, Mail, UserRound } from 'lucide-react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { AuthLayout } from '../components/AuthLayout'
import { BotCheckWidget } from '../components/BotCheckWidget'
import { getPublicConfig } from '../lib/api'
import { useRegister } from '../lib/auth'
import { loginUrl, safeReturnTo } from '../lib/navigation'
import { registrationError } from '../lib/registration'

export function RegisterPage() {
  const [displayName, setDisplayName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [validation, setValidation] = useState('')
  const registration = useRegister()
  const config = useQuery({ queryKey: ['public-config'], queryFn: ({ signal }) => getPublicConfig(signal), staleTime: Infinity, retry: 1 })
  const siteKey = config.data?.botCheckSiteKey ?? null
  const [botCheckToken, setBotCheckToken] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)
  const navigate = useNavigate()
  const [search] = useSearchParams()
  const returnTo = safeReturnTo(search.get('returnTo'))
  const isInvitation = returnTo.startsWith('/invite/')
  const error = validation || registration.error?.message

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const input = { displayName, email, password }
    const message = registrationError(input, confirmation)
      || (siteKey && !botCheckToken ? 'Poczekaj, aż weryfikacja pod formularzem się zakończy.' : '')
    setValidation(message)
    if (message) return
    try {
      await registration.mutateAsync({ ...input, botCheckToken })
      setPassword(''); setConfirmation('')
      navigate(loginUrl(returnTo), { replace: true, state: { registered: true, email: email.trim() } })
    } catch {
      // The mutation exposes a readable error; writes are never retried. A used token does not work twice.
      if (siteKey) setAttempt(value => value + 1)
    }
  }

  return <AuthLayout>
    <h2>Utwórz konto</h2>
    <p className="form-intro">{isInvitation ? 'Załóż konto, aby po zalogowaniu wrócić do zaproszenia i dołączyć do zespołu.' : 'Zacznij rejestrować czas i pracować ze swoim zespołem.'}</p>
    <form aria-label="Rejestracja konta" onSubmit={submit} onInput={() => { setValidation(''); if (registration.isError) registration.reset() }}>
      <fieldset disabled={registration.isPending}>
        <div className="field-group"><label htmlFor="register-name">Nazwa wyświetlana</label>
          <div className="input-wrap"><UserRound size={17} aria-hidden="true" /><input id="register-name" name="displayName" autoComplete="name" maxLength={255} required value={displayName} onChange={event => setDisplayName(event.target.value)} /></div>
        </div>
        <div className="field-group"><label htmlFor="register-email">Adres e-mail</label>
          <div className="input-wrap"><Mail size={17} aria-hidden="true" /><input id="register-email" name="email" type="email" autoComplete="username" maxLength={255} required value={email} onChange={event => setEmail(event.target.value)} /></div>
        </div>
        <div className="field-group"><label htmlFor="register-password">Hasło</label>
          <div className="input-wrap"><LockKeyhole size={17} aria-hidden="true" /><input id="register-password" name="password" type={showPassword ? 'text' : 'password'} autoComplete="new-password" maxLength={72} required value={password} aria-describedby="register-password-hint" onChange={event => setPassword(event.target.value)} />
            <button className="password-toggle" type="button" aria-label={showPassword ? 'Ukryj hasło' : 'Pokaż hasło'} aria-pressed={showPassword} onClick={() => setShowPassword(!showPassword)}>{showPassword ? <EyeOff size={17} /> : <Eye size={17} />}</button>
          </div><p className="field-hint" id="register-password-hint">Co najmniej 12 znaków. Możesz używać spacji i znaków specjalnych.</p>
        </div>
        <div className="field-group"><label htmlFor="register-confirmation">Powtórz hasło</label>
          <div className="input-wrap"><LockKeyhole size={17} aria-hidden="true" /><input id="register-confirmation" name="passwordConfirmation" type={showPassword ? 'text' : 'password'} autoComplete="new-password" maxLength={72} required value={confirmation} onChange={event => setConfirmation(event.target.value)} /></div>
        </div>
      </fieldset>
      {siteKey && <BotCheckWidget siteKey={siteKey} attempt={attempt} onToken={setBotCheckToken} />}
      {error && <p className="form-error" role="alert">{error}</p>}
      <button className="button-primary login-submit" type="submit" disabled={registration.isPending} aria-busy={registration.isPending}>
        {registration.isPending ? <><LoaderCircle size={17} className="spin" />Tworzenie konta…</> : <>Utwórz konto<ArrowRight size={17} /></>}
      </button>
    </form>
    <p className="auth-alternative">Masz już konto? <Link className="text-link" to={loginUrl(returnTo)}>Zaloguj się</Link></p>
  </AuthLayout>
}
