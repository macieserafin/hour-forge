import { useEffect, useRef } from 'react'
import { CheckCircle2, CircleAlert, LoaderCircle } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { AuthLayout } from '../components/AuthLayout'
import { ApiError } from '../lib/api'
import { useConfirmAccountDeletion } from '../lib/account'
import { useSession } from '../lib/auth'

export function ConfirmAccountDeletionPage() {
  const { token = '' } = useParams()
  const confirm = useConfirmAccountDeletion()
  // Somebody may open the link while signed in to another account; the link always means the account the e-mail went to.
  const signedIn = useSession().data
  const result = useRef<HTMLParagraphElement>(null)
  const status = confirm.error instanceof ApiError ? confirm.error.status : null
  // A used, expired or unknown link cannot work again; a team owner can come back with the same link.
  const linkDead = status === 400
  // The button that had the focus may be gone, so the answer takes it.
  useEffect(() => { if (confirm.isSuccess || confirm.isError) result.current?.focus() }, [confirm.isSuccess, confirm.isError, confirm.error])

  return <AuthLayout>
    <h2>Usunięcie konta</h2>
    {confirm.isSuccess ? <>
      <p className="project-notice auth-success" role="status" tabIndex={-1} ref={result}><CheckCircle2 size={16} aria-hidden="true" /> Konto zostało usunięte. Wszystkie urządzenia tego konta zostały wylogowane.</p>
      {signedIn && <p className="field-hint">Ta przeglądarka pozostaje zalogowana na konto {signedIn.email}, bo link dotyczył innego konta.</p>}
      <p className="auth-alternative"><Link className="text-link" to="/login">Przejdź do logowania</Link></p>
    </> : <>
      {/* Mail scanners open links on their own, so nothing is removed until the button is pressed. */}
      {signedIn && !linkDead && <p className="field-hint">Ta przeglądarka jest zalogowana na konto {signedIn.email}. Link usuwa konto, na które wysłaliśmy wiadomość z tym linkiem.</p>}
      {!linkDead && <p className="form-intro">Usuniemy Twoje imię, adres e-mail i dane logowania. Zapisane godziny pozostaną w raportach zespołów pod nazwą „Użytkownik usunięty”. Trwający timer zostanie odrzucony bez zapisania jego czasu. Tej operacji nie można cofnąć. Jeśli nie chcesz usuwać konta, zamknij tę stronę.</p>}
      {confirm.isError && <p className="form-error" role="alert" tabIndex={-1} ref={result}><CircleAlert size={16} aria-hidden="true" /> {confirm.error.message}
        {status === 409 && <> <Link className="text-link" to="/teams">Przejdź do zespołów</Link></>}</p>}
      {!linkDead && <button className="button-primary button-danger login-submit" type="button" disabled={confirm.isPending} aria-busy={confirm.isPending}
        onClick={() => confirm.mutate(token)}>
        {confirm.isPending ? <><LoaderCircle size={17} className="spin" />Usuwanie…</> : 'Usuń konto na stałe'}</button>}
      <p className="auth-alternative">{linkDead
        ? <Link className="text-link" to="/account">Przejdź do ustawień konta</Link>
        : <Link className="text-link" to="/login">Przejdź do logowania</Link>}</p>
    </>}
  </AuthLayout>
}
