import { useState } from 'react'
import { ChevronLeft, ChevronRight, Crown, LoaderCircle, LogOut, RefreshCw, ShieldCheck, ShieldOff, TimerReset, UserMinus } from 'lucide-react'
import { Modal } from '../components/Modal'
import { MemberCorrections } from './MemberCorrections'
import { canManage, roleLabel, type Team, type TeamMember } from '../lib/api'
import { useEndMembership, useTeamMembers } from '../lib/members'
import { useChangeRole, useTransferOwnership } from '../lib/teams'

function TransferOwnershipModal({ userId, team, member, onClose, onComplete }: {
  userId: string; team: Team; member: TeamMember; onClose: () => void; onComplete: () => void
}) {
  const transfer = useTransferOwnership(userId, team.id)
  return <Modal title="Przekaż własność zespołu" busy={transfer.isPending} onClose={onClose}>
    <p className="dialog-description">Osoba <strong>{member.displayName}</strong> zostanie właścicielem zespołu <strong>{team.name}</strong>.</p>
    <p className="field-hint">Zostaniesz zwykłym członkiem: stracisz możliwość zarządzania członkami, projektami, zaproszeniami i raportami zespołu. Jeśli ta osoba jest administratorem, jej rola zmieni się na właściciela. Później możesz opuścić zespół.</p>
    {transfer.isError && <p role="alert" className="form-error">{transfer.error.message}</p>}
    <footer className="dialog-actions"><button className="button-secondary" disabled={transfer.isPending} onClick={onClose}>Anuluj</button>
      <button className="button-primary button-danger" disabled={transfer.isPending} onClick={() => transfer.mutate(member.userId, { onSuccess: onComplete })}>
        {transfer.isPending && <LoaderCircle size={16} className="spin" />}{transfer.isPending ? 'Zapisywanie…' : 'Przekaż własność'}
      </button>
    </footer>
  </Modal>
}

function EndMembershipModal({ userId, team, member, onClose, onComplete }: {
  userId: string; team: Team; member: TeamMember | null; onClose: () => void; onComplete: () => void
}) {
  const end = useEndMembership(userId, team.id)
  const leaving = member === null
  async function confirm() {
    try {
      await end.mutateAsync(member?.userId ?? null)
      onComplete()
    } catch { /* The mutation exposes the error and reconciles the current server state. */ }
  }
  return <Modal title={leaving ? 'Opuść zespół' : 'Usuń członka zespołu'} busy={end.isPending} onClose={onClose}>
    <p className="dialog-description">{leaving ? <>Opuścisz zespół <strong>{team.name}</strong> i utracisz dostęp do jego danych.</>
      : <>Osoba <strong>{member.displayName}</strong> utraci dostęp do zespołu <strong>{team.name}</strong>.</>}</p>
    <p className="field-hint">Historia pracy pozostanie w raportach zespołu. Uruchomiony timer będzie można zakończyć, ale nie wznowić. Ponowne dołączenie wymaga ważnego zaproszenia.</p>
    {end.isError && <p role="alert" className="form-error">{end.error.message}</p>}
    <footer className="dialog-actions"><button className="button-secondary" disabled={end.isPending} onClick={onClose}>Anuluj</button>
      <button className="button-primary button-danger" disabled={end.isPending} onClick={() => void confirm()}>
        {end.isPending && <LoaderCircle size={16} className="spin" />}{end.isPending ? 'Zapisywanie…' : leaving ? 'Potwierdź opuszczenie' : 'Potwierdź usunięcie'}
      </button>
    </footer>
  </Modal>
}

export function TeamMembersPanel({ userId, team, onLeft }: { userId: string; team: Team; onLeft: () => void }) {
  const owner = team.role === 'OWNER'
  const manager = canManage(team.role)
  const changeRole = useChangeRole(userId, team.id)
  const [includeFormer, setIncludeFormer] = useState(false)
  const [page, setPage] = useState(0)
  const [notice, setNotice] = useState('')
  const [action, setAction] = useState<{ member: TeamMember | null } | null>(null)
  const [transferTarget, setTransferTarget] = useState<TeamMember | null>(null)
  const [correcting, setCorrecting] = useState<TeamMember | null>(null)
  const members = useTeamMembers(userId, team.id, includeFormer, page)
  const dateLabel = (date: string) => new Date(date).toLocaleString('pl-PL', { dateStyle: 'medium', timeStyle: 'short', timeZone: team.timezone })

  return <section className="team-members" aria-label="Członkowie zespołu">
    <header className="teams-toolbar"><div><h2>Członkowie zespołu</h2><p>{manager ? 'Zarządzaj dostępem do wybranego zespołu.' : 'Osoby, z którymi pracujesz w tym zespole.'}</p></div>
      <div className="team-actions"><button className="button-secondary" disabled={members.isFetching} onClick={() => void members.refetch()}><RefreshCw size={15} className={members.isFetching ? 'spin' : ''} />Odśwież członków</button>
        {!owner && <button className="button-secondary" onClick={() => { setNotice(''); setAction({ member: null }) }}><LogOut size={15} />Opuść zespół</button>}
      </div>
    </header>
    {manager && <div className="projects-filter"><label><input type="checkbox" checked={includeFormer} onChange={event => { setIncludeFormer(event.target.checked); setPage(0); setNotice('') }} />Pokaż również byłych członków</label></div>}
    {notice && <p className="project-notice" role="status">{notice}</p>}
    {changeRole.isError && <p className="form-error" role="alert">{changeRole.error.message}</p>}
    {members.isPending ? <p className="teams-loading" role="status"><LoaderCircle size={18} className="spin" />Wczytywanie członków…</p>
      : members.isError ? <div className="dashboard-error" role="alert">{members.error.message}<button onClick={() => void members.refetch()}>Wczytaj członków ponownie</button></div>
      : <>
        <p className="history-result-count">Liczba osób: {members.data.totalElements} · Daty w strefie {team.timezone}</p>
        {members.data.content.length === 0 ? <p className="report-empty">Na tej stronie nie ma członków. Wróć na pierwszą stronę lub odśwież listę.</p>
          : <div className="team-table-wrap" tabIndex={0} role="region" aria-label="Lista członków zespołu" aria-busy={members.isFetching}>
            <table className="team-table members-table"><caption className="sr-only">Członkowie zespołu {team.name}</caption>
              <thead><tr><th scope="col">Osoba</th><th scope="col">Rola</th><th scope="col">Dołączono</th><th scope="col">Status</th>{manager && <th scope="col">Akcje</th>}</tr></thead>
              <tbody>{members.data.content.map(member => <tr key={member.userId}>
                <td data-label="Osoba">{member.displayName}{member.userId === userId && <span className="report-meta">To Ty</span>}</td>
                <td data-label="Rola">{roleLabel(member.role)}</td>
                <td data-label="Dołączono"><time dateTime={member.joinedAt}>{dateLabel(member.joinedAt)}</time></td>
                <td data-label="Status">{member.leftAt ? <>Były członek<span className="report-meta">Odejście: <time dateTime={member.leftAt}>{dateLabel(member.leftAt)}</time></span></> : 'Aktywny'}</td>
                {manager && <td data-label="Akcje"><div className="history-actions">
                  {member.userId !== userId && <button className="text-link" aria-label={`Korekty czasu: ${member.displayName}`} onClick={() => { setNotice(''); setCorrecting(member) }}><TimerReset size={15} />Korekty czasu</button>}
                  {member.userId !== userId && !member.leftAt && member.role !== 'OWNER' && (owner || member.role === 'MEMBER') && <>
                    <button className="text-link" aria-label={`Usuń z zespołu: ${member.displayName}`} onClick={() => { setNotice(''); setAction({ member }) }}><UserMinus size={15} />Usuń z zespołu</button>
                    {owner && <button className="text-link" disabled={changeRole.isPending} aria-label={`${member.role === 'ADMIN' ? 'Odbierz administratora' : 'Nadaj administratora'}: ${member.displayName}`}
                      onClick={() => { setNotice(''); changeRole.mutate({ targetUserId: member.userId, role: member.role === 'ADMIN' ? 'MEMBER' : 'ADMIN' }, { onSuccess: () => setNotice(member.role === 'ADMIN' ? `Osoba ${member.displayName} nie jest już administratorem.` : `Osoba ${member.displayName} jest administratorem zespołu.`) }) }}>
                      {member.role === 'ADMIN' ? <ShieldOff size={15} /> : <ShieldCheck size={15} />}{member.role === 'ADMIN' ? 'Odbierz administratora' : 'Nadaj administratora'}</button>}
                    {owner && <button className="text-link" aria-label={`Przekaż własność: ${member.displayName}`} onClick={() => { setNotice(''); setTransferTarget(member) }}><Crown size={15} />Przekaż własność</button>}</>}
                  {member.leftAt && <span className="field-hint">Dostęp zakończony</span>}
                  {member.role === 'OWNER' && member.userId === userId && <span className="field-hint">Właściciel zespołu</span>}</div></td>}
              </tr>)}</tbody>
            </table>
          </div>}
        {members.data.totalPages > 0 && <nav className="history-pagination" aria-label="Strony członków zespołu">
          <p>Strona {members.data.page + 1} z {members.data.totalPages}</p><div>
            <button className="button-secondary" disabled={page === 0 || members.isFetching} onClick={() => setPage(page - 1)}><ChevronLeft size={16} />Poprzednia</button>
            <button className="button-secondary" disabled={page + 1 >= members.data.totalPages || members.isFetching} onClick={() => setPage(page + 1)}>Następna<ChevronRight size={16} /></button>
          </div>
        </nav>}
        {page > 0 && members.data.content.length === 0 && <button className="button-secondary" onClick={() => setPage(0)}>Wróć na pierwszą stronę</button>}
      </>}
    {correcting && <MemberCorrections userId={userId} team={team} member={correcting} onClose={() => setCorrecting(null)} />}
    {transferTarget && <TransferOwnershipModal userId={userId} team={team} member={transferTarget} onClose={() => setTransferTarget(null)} onComplete={() => setTransferTarget(null)} />}
    {action && <EndMembershipModal userId={userId} team={team} member={action.member} onClose={() => setAction(null)} onComplete={() => {
      setAction(null)
      if (action.member === null) onLeft()
      else {
        setNotice(`Zakończono dostęp osoby ${action.member.displayName} do zespołu ${team.name}.`)
        if (!includeFormer && page > 0 && members.data?.content.length === 1) setPage(page - 1)
      }
    }} />}
  </section>
}
