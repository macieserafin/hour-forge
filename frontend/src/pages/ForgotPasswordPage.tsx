import { useState, type FormEvent } from 'react'
import { LoaderCircle, Mail } from 'lucide-react'
import { Link } from 'react-router-dom'
import { AuthLayout } from '../components/AuthLayout'
import { useRequestPasswordReset } from '../lib/auth'

export function ForgotPasswordPage() {
  const request = useRequestPasswordReset()
  const [email, setEmail] = useState('')
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    request.mutate(email)
  }
  return <AuthLayout>
    <h2>Nie pamiętasz hasła?</h2>
    {request.isSuccess ? <p className="project-notice auth-success" role="status">Jeśli konto o takim adresie istnieje, wysłaliśmy na nie link do ustawienia nowego hasła. Link jest ważny przez godzinę.</p>
      : <>
        <p className="form-intro">Podaj adres e-mail konta. Wyślemy link do ustawienia nowego hasła.</p>
        <form onSubmit={submit} aria-label="Reset hasła">
          <div className="field-group"><label htmlFor="forgot-email">Adres e-mail</label>
            <div className="input-wrap"><Mail size={17} aria-hidden="true" />
              <input id="forgot-email" type="email" autoComplete="username" maxLength={255} required value={email} disabled={request.isPending}
                onChange={event => { setEmail(event.target.value); if (request.isError) request.reset() }} /></div></div>
          {request.isError && <p className="form-error" role="alert">{request.error.message}</p>}
          <button className="button-primary login-submit" type="submit" disabled={request.isPending}>
            {request.isPending && <LoaderCircle size={17} className="spin" />}{request.isPending ? 'Wysyłanie…' : 'Wyślij link'}</button>
        </form></>}
    <p className="auth-alternative"><Link className="text-link" to="/login">Wróć do logowania</Link></p>
  </AuthLayout>
}
