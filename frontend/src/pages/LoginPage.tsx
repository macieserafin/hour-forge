import { useState, type FormEvent } from 'react'
import { ArrowRight, Eye, EyeOff, LoaderCircle, LockKeyhole, Mail } from 'lucide-react'
import { AuthLayout } from '../components/AuthLayout'
import { useLogin, useResendVerification } from '../lib/auth'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { registrationUrl, safeReturnTo } from '../lib/navigation'

export function LoginPage() {
  const location = useLocation()
  const registered = location.state?.registered === true
  const [email, setEmail] = useState(() => registered && typeof location.state?.email === 'string' ? location.state.email : '')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [rememberMe, setRememberMe] = useState(false)
  const login = useLogin()
  const resend = useResendVerification()
  const [search] = useSearchParams()
  const isInvitation = safeReturnTo(search.get('returnTo')).startsWith('/invite/')

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    login.mutate({ email, password, rememberMe }, { onSuccess: () => setPassword('') })
  }

  return <AuthLayout>
        <h2>Zaloguj się</h2>
        <p className="form-intro">{isInvitation ? 'Zaloguj się, aby wrócić do zaproszenia i potwierdzić dołączenie do zespołu.' : 'Użyj adresu e-mail przypisanego do Twojego konta.'}</p>
        {registered && <p className="project-notice auth-success" role="status">Rejestracja przyjęta. Wysłaliśmy wiadomość na podany adres e-mail. Potwierdź adres linkiem z wiadomości, a potem zaloguj się.</p>}

        <form onSubmit={submit} aria-label="Logowanie">
          <div className="field-group">
            <label htmlFor="email">Adres e-mail</label>
            <div className="input-wrap"><Mail size={17} aria-hidden="true" />
              <input id="email" name="email" type="email" autoComplete="username" placeholder="ty@firma.pl"
                required value={email} disabled={login.isPending} aria-invalid={login.isError}
                aria-describedby={login.isError ? 'login-error' : undefined}
                onChange={event => { setEmail(event.target.value); if (login.isError) login.reset() }} />
            </div>
          </div>
          <div className="field-group">
            <label htmlFor="password">Hasło</label>
            <div className="input-wrap"><LockKeyhole size={17} aria-hidden="true" />
              <input id="password" name="password" type={showPassword ? 'text' : 'password'} autoComplete="current-password"
                placeholder="Wpisz swoje hasło" required value={password} disabled={login.isPending}
                aria-invalid={login.isError} aria-describedby={login.isError ? 'login-error' : undefined}
                onChange={event => { setPassword(event.target.value); if (login.isError) login.reset() }} />
              <button type="button" className="password-toggle" aria-label={showPassword ? 'Ukryj hasło' : 'Pokaż hasło'}
                aria-pressed={showPassword} onClick={() => setShowPassword(!showPassword)}>
                {showPassword ? <EyeOff size={17} /> : <Eye size={17} />}
              </button>
            </div>
          </div>
          <label className="remember-me"><input type="checkbox" checked={rememberMe} disabled={login.isPending} onChange={event => setRememberMe(event.target.checked)} />Zapamiętaj mnie na tym urządzeniu (30 dni)</label>
          <p className="field-hint"><Link className="text-link" to="/forgot-password">Nie pamiętasz hasła?</Link></p>
          {login.isError && <p className="form-error" role="alert" id="login-error">{login.error.message}</p>}
          {login.isError && (login.error as { code?: string }).code === 'EMAIL_NOT_VERIFIED' && <div className="field-hint">
            <button type="button" className="text-link" disabled={resend.isPending} onClick={() => resend.mutate(email)}>Wyślij link potwierdzający ponownie</button>
            {resend.isSuccess && <p role="status">Jeśli konto czeka na potwierdzenie, wysłaliśmy nowy link.</p>}
            {resend.isError && <p className="form-error" role="alert">{resend.error.message}</p>}
          </div>}
          <button className="button-primary login-submit" type="submit" disabled={login.isPending} aria-busy={login.isPending}>
            {login.isPending ? <><LoaderCircle size={17} className="spin" />Logowanie…</> : <>Zaloguj się<ArrowRight size={17} /></>}
          </button>
        </form>
        <p className="auth-alternative">Nie masz konta? <Link className="text-link" to={registrationUrl(safeReturnTo(search.get('returnTo')))}>Utwórz konto</Link></p>
  </AuthLayout>
}
