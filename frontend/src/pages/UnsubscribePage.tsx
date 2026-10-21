import { useEffect, useRef } from 'react'
import { CheckCircle2, CircleAlert, LoaderCircle } from 'lucide-react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { AuthLayout } from '../components/AuthLayout'
import { unsubscribe, type UnsubscribeKind } from '../lib/api'

const labels: Record<UnsubscribeKind, string> = {
  TEAM_EVENTS: 'wiadomości o zmianach w zespołach',
  WEEKLY_REPORT: 'cotygodniowych raportów zespołu',
  LONG_TIMER: 'przypomnień o zapomnianym timerze',
}

export function UnsubscribePage() {
  const { token = '' } = useParams()
  const [search] = useSearchParams()
  const raw = search.get('kind')
  const kind = raw && raw in labels ? raw as UnsubscribeKind : null
  const done = useMutation({ mutationFn: unsubscribe, retry: false, gcTime: 0 })
  const started = useRef(false)
  // Switching a kind off is safe to repeat, but the request is still sent only once per visit.
  useEffect(() => {
    if (started.current || !kind) return
    started.current = true
    done.mutate({ token, kind })
  }, [done, token, kind])
  return <AuthLayout>
    <h2>Powiadomienia e-mail</h2>
    {!kind ? <p className="form-error" role="alert"><CircleAlert size={16} aria-hidden="true" /> Ten link jest niekompletny. Ustawienia powiadomień znajdziesz w koncie.</p>
      : done.isError ? <p className="form-error" role="alert"><CircleAlert size={16} aria-hidden="true" /> {done.error.message}</p>
      : done.isSuccess ? <p className="project-notice auth-success" role="status"><CheckCircle2 size={16} aria-hidden="true" /> Nie będziemy już wysyłać {labels[kind]}.</p>
      : <p role="status"><LoaderCircle size={17} className="spin" /> Zapisywanie ustawienia…</p>}
    <p className="auth-alternative"><Link className="text-link" to="/account">Ustawienia powiadomień w koncie</Link></p>
  </AuthLayout>
}
