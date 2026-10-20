import { useEffect, useRef } from 'react'
import { CheckCircle2, CircleAlert, LoaderCircle } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { AuthLayout } from '../components/AuthLayout'
import { useConfirmEmailChange } from '../lib/auth'

export function ConfirmEmailChangePage() {
  const { token = '' } = useParams()
  const confirm = useConfirmEmailChange()
  const started = useRef(false)
  // The link is single-use, so it is redeemed exactly once even when effects run twice in development.
  useEffect(() => {
    if (started.current) return
    started.current = true
    confirm.mutate(token)
  }, [confirm, token])

  return <AuthLayout>
    <h2>Zmiana adresu e-mail</h2>
    {confirm.isError ? <p className="form-error" role="alert"><CircleAlert size={16} aria-hidden="true" /> {confirm.error.message}</p>
      : confirm.isSuccess ? <>
        <p className="project-notice auth-success" role="status"><CheckCircle2 size={16} aria-hidden="true" /> Adres e-mail został zmieniony. Zaloguj się nowym adresem.</p>
      </> : <p role="status"><LoaderCircle size={17} className="spin" /> Potwierdzanie zmiany…</p>}
    <p className="auth-alternative"><Link className="text-link" to="/login">Przejdź do logowania</Link></p>
  </AuthLayout>
}
