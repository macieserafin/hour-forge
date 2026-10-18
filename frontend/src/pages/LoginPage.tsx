import { useState, type FormEvent } from 'react'
import { ArrowRight, Eye, EyeOff, LoaderCircle, LockKeyhole, Mail } from 'lucide-react'
import { Brand } from '../components/Brand'
import { useLogin } from '../lib/auth'

export function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const login = useLogin()

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    login.mutate({ email, password }, { onSuccess: () => setPassword('') })
  }

  return <main className="login-layout">
    <section className="login-story" aria-label="HourForge — przestrzeń do pracy">
      <Brand />
      <div className="story-content">
        <p className="product-category">REJESTRACJA CZASU PRACY</p>
        <h1>Czas pracy<br />Twojego zespołu.</h1>
        <p>Rejestracja pracy nad projektami w jednym panelu, dla małych zespołów.</p>
      </div>
      <div className="story-footer"><span>HourForge</span><span>Panel użytkownika</span></div>
    </section>

    <section className="login-form-panel">
      <div className="login-top"><span className="mobile-brand"><Brand /></span><span className="login-context">Panel użytkownika</span></div>
      <div className="login-form-wrap">
        <h2>Zaloguj się</h2>
        <p className="form-intro">Użyj adresu e-mail przypisanego do Twojego konta.</p>

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
          {login.isError && <p className="form-error" role="alert" id="login-error">{login.error.message}</p>}
          <button className="button-primary login-submit" type="submit" disabled={login.isPending} aria-busy={login.isPending}>
            {login.isPending ? <><LoaderCircle size={17} className="spin" />Logowanie…</> : <>Zaloguj się<ArrowRight size={17} /></>}
          </button>
        </form>
      </div>
      <footer className="login-footer"><span>© {new Date().getFullYear()} HourForge</span></footer>
    </section>
  </main>
}
