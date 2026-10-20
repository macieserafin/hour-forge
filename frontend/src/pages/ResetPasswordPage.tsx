import { useState, type FormEvent } from 'react'
import { LoaderCircle, LockKeyhole } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { AuthLayout } from '../components/AuthLayout'
import { useConfirmPasswordReset } from '../lib/auth'
import { registrationError } from '../lib/registration'

export function ResetPasswordPage() {
  const { token = '' } = useParams()
  const reset = useConfirmPasswordReset()
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [validation, setValidation] = useState('')
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    // Only the password rules matter here; the name and address of the sign-up check are filled with valid placeholders.
    const message = registrationError({ displayName: 'x', email: 'x', password }, confirmation)
    setValidation(message)
    if (message) return
    reset.mutate({ token, newPassword: password }, { onSuccess: () => { setPassword(''); setConfirmation('') } })
  }
  return <AuthLayout>
    <h2>Ustaw nowe hasło</h2>
    {reset.isSuccess ? <>
      <p className="project-notice auth-success" role="status">Hasło zostało zmienione. Wszystkie urządzenia zostały wylogowane.</p>
      <p className="auth-alternative"><Link className="text-link" to="/login">Przejdź do logowania</Link></p>
    </> : <form onSubmit={submit} aria-label="Ustawianie hasła" onInput={() => { setValidation(''); if (reset.isError) reset.reset() }}>
      <fieldset disabled={reset.isPending}>
        <div className="field-group"><label htmlFor="reset-password">Nowe hasło</label>
          <div className="input-wrap"><LockKeyhole size={17} aria-hidden="true" />
            <input id="reset-password" type="password" autoComplete="new-password" maxLength={72} required value={password} onChange={event => setPassword(event.target.value)} /></div>
          <p className="field-hint">Co najmniej 12 znaków. Możesz używać spacji i znaków specjalnych.</p></div>
        <div className="field-group"><label htmlFor="reset-confirmation">Powtórz nowe hasło</label>
          <div className="input-wrap"><LockKeyhole size={17} aria-hidden="true" />
            <input id="reset-confirmation" type="password" autoComplete="new-password" maxLength={72} required value={confirmation} onChange={event => setConfirmation(event.target.value)} /></div></div>
      </fieldset>
      {(validation || reset.isError) && <p className="form-error" role="alert">{validation || reset.error?.message}</p>}
      <button className="button-primary login-submit" type="submit" disabled={reset.isPending}>
        {reset.isPending && <LoaderCircle size={17} className="spin" />}{reset.isPending ? 'Zapisywanie…' : 'Ustaw hasło'}</button>
      <p className="auth-alternative"><Link className="text-link" to="/forgot-password">Poproś o nowy link</Link></p>
    </form>}
  </AuthLayout>
}
