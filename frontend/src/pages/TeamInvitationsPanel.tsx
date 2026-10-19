import { useState } from 'react'
import { LoaderCircle, RefreshCw } from 'lucide-react'
import type { InvitationSummary, Team } from '../lib/api'
import { useInvitations, useRevokeInvitation } from '../lib/teams'
import { Modal } from '../components/Modal'

const dateLabel = (value: string) => new Date(value).toLocaleString('pl-PL', { dateStyle: 'medium', timeStyle: 'short' })
const statusLabel: Record<InvitationSummary['status'], string> = {
  ACTIVE: 'Aktywne', EXPIRED: 'Wygasłe', EXHAUSTED: 'Wykorzystane', REVOKED: 'Unieważnione',
}

export function TeamInvitationsPanel({ userId, team }: { userId: string; team: Team }) {
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<InvitationSummary | null>(null)
  const list = useInvitations(userId, team.id, page)
  const revoke = useRevokeInvitation(userId, team.id)
  return <section className="time-history" aria-label="Zaproszenia zespołu">
    <header className="teams-toolbar"><div><h3>Zaproszenia zespołu</h3><p>Link jest dostępny tylko przy utworzeniu. Lista nie pozwala go odzyskać.</p></div>
      <button className="button-secondary" disabled={list.isFetching} onClick={() => void list.refetch()}><RefreshCw size={15} />Odśwież zaproszenia</button></header>
    {list.isPending ? <p role="status"><LoaderCircle size={18} className="spin" />Wczytywanie zaproszeń…</p>
      : list.isError ? <div role="alert" className="dashboard-error">{list.error.message}<button onClick={() => void list.refetch()}>Wczytaj zaproszenia ponownie</button></div>
      : <>
        <p role="status">Liczba zaproszeń: {list.data.totalElements}</p>
        {list.data.content.length === 0 ? <p>{page ? 'Na tej stronie nie ma zaproszeń. Wróć na poprzednią stronę.' : 'Nie utworzono jeszcze zaproszeń.'}</p>
          : <div className="team-table-wrap" tabIndex={0} role="region" aria-label="Lista zaproszeń" aria-busy={list.isFetching}>
            <table className="team-table"><caption className="sr-only">Zaproszenia od najnowszego</caption>
              <thead><tr><th scope="col">Utworzono</th><th scope="col">Ważne do</th><th scope="col">Użycia / limit</th><th scope="col">Status</th>{!team.archivedAt && <th scope="col">Akcje</th>}</tr></thead>
              <tbody>{list.data.content.map(invite => <tr key={invite.id}>
                <td><time dateTime={invite.createdAt}>{dateLabel(invite.createdAt)}</time></td>
                <td><time dateTime={invite.expiresAt}>{dateLabel(invite.expiresAt)}</time></td>
                <td>{invite.usedCount} / {invite.maxUses ?? 'Bez limitu'}</td><td>{statusLabel[invite.status]}</td>
                {!team.archivedAt && <td>{invite.status === 'ACTIVE' && <button className="text-link" disabled={revoke.isPending} onClick={() => { revoke.reset(); setSelected(invite) }}>Unieważnij</button>}</td>}
              </tr>)}</tbody>
            </table>
          </div>}
        {list.data.totalPages > 0 && <nav className="history-pagination" aria-label="Strony zaproszeń">
          <p>Strona {list.data.page + 1} z {list.data.totalPages}</p><div>
            <button className="button-secondary" disabled={page === 0 || list.isFetching} onClick={() => setPage(page - 1)}>Poprzednia</button>
            <button className="button-secondary" disabled={page + 1 >= list.data.totalPages || list.isFetching} onClick={() => setPage(page + 1)}>Następna</button>
          </div></nav>}
      </>}
    {selected && !list.isError && !team.archivedAt && <Modal title="Unieważnij zaproszenie" busy={revoke.isPending} onClose={() => setSelected(null)}>
      <p className="dialog-description">Unieważnić zaproszenie do zespołu <strong>{team.name}</strong> utworzone {dateLabel(selected.createdAt)}? Link przestanie pozwalać na dołączenie. Obecni członkowie zachowają dostęp.</p>
      {revoke.isError && <p role="alert" className="form-error">{revoke.error.message}</p>}
      <footer className="dialog-actions"><button className="button-secondary" disabled={revoke.isPending} onClick={() => setSelected(null)}>Anuluj</button>
        <button className="button-primary button-danger" disabled={revoke.isPending} onClick={() => revoke.mutate(selected.id, { onSuccess: () => setSelected(null) })}>{revoke.isPending ? 'Unieważnianie…' : 'Unieważnij zaproszenie'}</button></footer>
    </Modal>}
  </section>
}
