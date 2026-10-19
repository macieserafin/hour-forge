import { ArrowRight, LoaderCircle, Users } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { Brand } from '../components/Brand'
import type { InvitationStatus, User } from '../lib/api'
import { useAcceptInvitation, useInvitationPreview } from '../lib/teams'

const unavailable: Record<Exclude<InvitationStatus, 'ACTIVE' | 'ALREADY_MEMBER'>, (team: string) => string> = {
  NEW_INVITATION_REQUIRED: team => `Po usunięciu z zespołu ${team} potrzebujesz zaproszenia utworzonego później. Poproś właściciela o nowy link.`,
  EXPIRED: team => `To zaproszenie do zespołu ${team} wygasło. Poproś właściciela o nowy link.`,
  REVOKED: team => `To zaproszenie do zespołu ${team} zostało unieważnione. Poproś właściciela o nowy link.`,
  EXHAUSTED: team => `Limit użyć zaproszenia do zespołu ${team} został wyczerpany. Poproś właściciela o nowy link.`,
  TEAM_ARCHIVED: team => `Zespół ${team} został zarchiwizowany i nie przyjmuje nowych członków.`,
}

export function InvitePage({ user }: { user: User }) {
  const { token = '' } = useParams()
  const preview = useInvitationPreview(user.id, token)
  const accept = useAcceptInvitation(user.id)
  const invitation = preview.data
  const teamLink = (teamId: string) => `/teams?team=${encodeURIComponent(teamId)}`

  return <main className="session-screen invitation-page">
    <Brand />
    <section className="connection-card">
      <Users size={25} strokeWidth={1.5} aria-hidden="true" />
      <h1>{accept.data ? 'Masz dostęp do zespołu' : 'Zaproszenie do zespołu'}</h1>
      {accept.data ? <><p>Możesz teraz wybrać zespół <strong>{accept.data.name}</strong> na swoim pulpicie.</p><Link className="button-primary" to={teamLink(accept.data.id)}>Przejdź do zespołu<ArrowRight size={16} /></Link></>
        : preview.isPending ? <p role="status" className="teams-loading"><LoaderCircle size={18} className="spin" />Sprawdzanie zaproszenia…</p>
        : preview.isError ? <p className="form-error" role="alert">{preview.error.message}</p>
        : invitation!.status === 'ALREADY_MEMBER' ? <><p>Należysz już do zespołu <strong>{invitation!.teamName}</strong>.</p><Link className="button-primary" to={teamLink(invitation!.teamId)}>Przejdź do zespołu<ArrowRight size={16} /></Link></>
        : invitation!.status !== 'ACTIVE' ? <p className="form-error" role="alert">{unavailable[invitation!.status](invitation!.teamName)}</p>
        : <><p>Zalogowano jako <strong className="invite-identity">{user.email}</strong>. Zespół <strong>{invitation!.teamName}</strong> zaprasza Cię do współpracy.</p>
          <p className="field-hint">Zaproszenie jest ważne do {new Date(invitation!.expiresAt).toLocaleString('pl-PL', { dateStyle: 'medium', timeStyle: 'short' })}.</p>
          {accept.isError && <p className="form-error" role="alert">{accept.error.message}</p>}
          <button className="button-primary" disabled={accept.isPending} onClick={() => accept.mutate(token)}>{accept.isPending && <LoaderCircle size={16} className="spin" />}{accept.isPending ? 'Dołączanie…' : 'Dołącz do zespołu'}</button></>}
      <Link className="text-link invite-back" to="/teams">Wróć do swoich zespołów</Link>
    </section>
  </main>
}
