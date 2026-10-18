import { ArrowRight, LoaderCircle, Users } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { Brand } from '../components/Brand'
import type { User } from '../lib/api'
import { useAcceptInvitation } from '../lib/teams'

export function InvitePage({ user }: { user: User }) {
  const { token = '' } = useParams()
  const accept = useAcceptInvitation(user.id)
  return <main className="session-screen invitation-page">
    <Brand />
    <section className="connection-card">
      <Users size={25} strokeWidth={1.5} aria-hidden="true" />
      <h1>{accept.data ? 'Masz dostęp do zespołu' : 'Zaproszenie do zespołu'}</h1>
      {accept.data ? <><p>Możesz teraz wybrać zespół <strong>{accept.data.name}</strong> na swoim pulpicie.</p><Link className="button-primary" to={`/teams?team=${encodeURIComponent(accept.data.id)}`}>Przejdź do zespołu<ArrowRight size={16} /></Link></>
        : <><p>Zalogowano jako <strong className="invite-identity">{user.email}</strong>. Potwierdź, że chcesz dołączyć do zespołu wskazanego w linku.</p>
          {accept.isError && <p className="form-error" role="alert">{accept.error.message}</p>}
          <button className="button-primary" disabled={accept.isPending} onClick={() => accept.mutate(token)}>{accept.isPending && <LoaderCircle size={16} className="spin" />}{accept.isPending ? 'Dołączanie…' : 'Dołącz do zespołu'}</button></>}
      <Link className="text-link invite-back" to="/teams">Wróć do swoich zespołów</Link>
    </section>
  </main>
}
