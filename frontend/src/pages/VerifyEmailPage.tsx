import { useEffect, useRef } from 'react'
import { CheckCircle2, CircleAlert, LoaderCircle } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { AuthLayout } from '../components/AuthLayout'
import { useConfirmEmail } from '../lib/auth'

export function VerifyEmailPage() {
  const { token = '' } = useParams()
  const confirm = useConfirmEmail()
  const started = useRef(false)
  // The link is single-use, so it is redeemed exactly once even when effects run twice in development.
  useEffect(() => {
    if (started.current) return
    started.current = true
    confirm.mutate(token)
  }, [confirm, token])

  return <AuthLayout>
    <h2>Potwierdzenie adresu e-mail</h2>
    {confirm.isError ? <>
      <p className="form-error" role="alert"><CircleAlert size={16} aria-hidden="true" /> {confirm.error.message}</p>
      <p className="auth-alternative"><Link className="text-link" to="/login">Przejdź do logowania</Link>, aby poprosić o nowy link.</p>
    </> : confirm.isSuccess ? <>
      <p className="project-notice auth-success" role="status"><CheckCircle2 size={16} aria-hidden="true" /> Adres e-mail został potwierdzony.</p>
      <p className="auth-alternative"><Link className="text-link" to="/login">Przejdź do logowania</Link></p>
    </> : <p role="status"><LoaderCircle size={17} className="spin" /> Potwierdzanie adresu…</p>}
  </AuthLayout>
}
