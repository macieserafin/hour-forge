import { useState, type FormEvent } from 'react'
import { LoaderCircle, Search } from 'lucide-react'
import { Modal } from '../components/Modal'
import type { OperatorAccount, OperatorTeam } from '../lib/api'
import { useOperatorAccounts, useOperatorActions, useOperatorStats, useOperatorTeams, usePublicConfig } from '../lib/operator'

const dateLabel = (value: string | null) => value ? new Date(value).toLocaleDateString('pl-PL', { dateStyle: 'medium' }) : '—'

function StatsSection() {
  const stats = useOperatorStats()
  return <section className="overview-panel account-section" aria-labelledby="operator-stats-title">
    <h2 id="operator-stats-title">Statystyki</h2>
    {stats.isPending ? <p role="status"><LoaderCircle size={18} className="spin" />Wczytywanie…</p>
      : stats.isError ? <div role="alert" className="dashboard-error">{stats.error.message}</div>
      : <dl className="operator-stats">
        <div><dt>Konta</dt><dd>{stats.data.accounts}</dd></div>
        <div><dt>Niepotwierdzone</dt><dd>{stats.data.unconfirmed}</dd></div>
        <div><dt>Zablokowane</dt><dd>{stats.data.blocked}</dd></div>
        <div><dt>Aktywne w 7 dni</dt><dd>{stats.data.activeLastWeek}</dd></div>
        <div><dt>Aktywne w 30 dni</dt><dd>{stats.data.activeLastMonth}</dd></div>
        <div><dt>Z logowaniem dwuetapowym</dt><dd>{stats.data.twoFactor}</dd></div>
        <div><dt>Zespoły</dt><dd>{stats.data.teams}</dd></div>
        <div><dt>Wpisy czasu</dt><dd>{stats.data.timeEntries}</dd></div>
      </dl>}
  </section>
}

function AnnouncementSection() {
  const config = usePublicConfig()
  const { announce, clearAnnouncement } = useOperatorActions()
  const current = config.data?.announcement ?? null
  const [message, setMessage] = useState<string | null>(null)
  const [level, setLevel] = useState<'INFO' | 'WARNING' | null>(null)
  const text = message ?? current?.message ?? ''
  const chosen = level ?? current?.level ?? 'INFO'

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!text.trim()) return
    announce.mutate({ message: text, level: chosen }, { onSuccess: () => { setMessage(null); setLevel(null) } })
  }

  return <section className="overview-panel account-section" aria-labelledby="operator-announcement-title">
    <h2 id="operator-announcement-title">Ogłoszenie dla wszystkich</h2>
    <p className="field-hint">Baner widać na każdej stronie, także przed zalogowaniem, na przykład przed pracami serwisowymi.</p>
    <form onSubmit={submit}>
      <div className="field-group"><label htmlFor="operator-announcement">Treść ogłoszenia</label>
        <textarea className="text-input" id="operator-announcement" rows={3} maxLength={500} value={text}
          onChange={event => { setMessage(event.target.value); announce.reset() }} /></div>
      <div className="field-group"><label htmlFor="operator-announcement-level">Rodzaj</label>
        <select className="text-input" id="operator-announcement-level" value={chosen} onChange={event => setLevel(event.target.value as 'INFO' | 'WARNING')}>
          <option value="INFO">Informacja</option><option value="WARNING">Ostrzeżenie</option>
        </select></div>
      {(announce.isError || clearAnnouncement.isError) && <p className="form-error" role="alert">{(announce.error ?? clearAnnouncement.error)?.message}</p>}
      {announce.isSuccess && <p className="project-notice" role="status">Ogłoszenie zostało opublikowane.</p>}
      {clearAnnouncement.isSuccess && !current && <p className="project-notice" role="status">Ogłoszenie zostało usunięte.</p>}
      <div className="dialog-actions">
        {current && <button type="button" className="button-secondary" disabled={clearAnnouncement.isPending}
          onClick={() => { announce.reset(); clearAnnouncement.mutate(undefined, { onSuccess: () => { setMessage(null); setLevel(null) } }) }}>Usuń ogłoszenie</button>}
        <button type="submit" className="button-primary" disabled={announce.isPending || !text.trim()}>
          {announce.isPending && <LoaderCircle size={16} className="spin" />}Opublikuj</button>
      </div>
    </form>
  </section>
}

function SearchForm({ label, onSearch }: { label: string; onSearch: (query: string) => void }) {
  const [query, setQuery] = useState('')
  const id = `operator-search-${label}`
  return <form className="operator-search" role="search" onSubmit={event => { event.preventDefault(); onSearch(query) }}>
    <label htmlFor={id}>{label}</label>
    <div><input className="text-input" id={id} maxLength={100} value={query} onChange={event => setQuery(event.target.value)} />
      <button type="submit" className="button-secondary"><Search size={15} />Szukaj</button></div>
  </form>
}

function Pager({ page, hasMore, onPage, label }: { page: number; hasMore: boolean; onPage: (page: number) => void; label: string }) {
  if (page === 0 && !hasMore) return null
  return <nav className="history-pagination" aria-label={label}><p>Strona {page + 1}</p><div>
    <button type="button" className="button-secondary" disabled={page === 0} onClick={() => onPage(page - 1)}>Poprzednia</button>
    <button type="button" className="button-secondary" disabled={!hasMore} onClick={() => onPage(page + 1)}>Następna</button>
  </div></nav>
}

type AccountDialog = { kind: 'block' | 'delete' | 'two-factor'; account: OperatorAccount }

function AccountsSection({ selfId }: { selfId: string }) {
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const accounts = useOperatorAccounts(query, page)
  const actions = useOperatorActions()
  const [dialog, setDialog] = useState<AccountDialog | null>(null)
  const [reason, setReason] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const busy = actions.block.isPending || actions.deleteAccount.isPending || actions.turnOffTwoFactor.isPending
  const dialogError = (dialog?.kind === 'block' ? actions.block.error : dialog?.kind === 'delete' ? actions.deleteAccount.error : actions.turnOffTwoFactor.error)?.message

  function open(kind: AccountDialog['kind'], account: OperatorAccount) {
    actions.block.reset(); actions.deleteAccount.reset(); actions.turnOffTwoFactor.reset()
    setReason(''); setConfirmation(''); setDialog({ kind, account })
  }
  const close = () => setDialog(null)

  return <section className="overview-panel account-section" aria-labelledby="operator-accounts-title">
    <h2 id="operator-accounts-title">Konta</h2>
    <SearchForm label="Szukaj konta po adresie lub nazwie" onSearch={value => { setQuery(value); setPage(0) }} />
    {actions.unblock.isError && <p className="form-error" role="alert">{actions.unblock.error.message}</p>}
    {accounts.isPending ? <p role="status"><LoaderCircle size={18} className="spin" />Wczytywanie kont…</p>
      : accounts.isError ? <div role="alert" className="dashboard-error">{accounts.error.message}</div>
      : accounts.data.items.length === 0 ? <p>Nie znaleziono kont.</p>
      : <div className="team-table-wrap" tabIndex={0} role="region" aria-label="Lista kont" aria-busy={accounts.isFetching}>
        <table className="team-table"><caption className="sr-only">Konta od najnowszego</caption>
          <thead><tr><th scope="col">Konto</th><th scope="col">Utworzono</th><th scope="col">Ostatnia aktywność</th><th scope="col">Stan</th><th scope="col">Akcje</th></tr></thead>
          <tbody>{accounts.data.items.map(account => <tr key={account.id}>
            <td><strong>{account.displayName}</strong><br /><span className="field-hint">{account.email}</span></td>
            <td>{dateLabel(account.createdAt)}</td>
            <td>{dateLabel(account.lastActiveAt)}</td>
            <td>{[account.blockedAt ? `Zablokowane: ${account.blockedReason ?? ''}` : null, account.confirmed ? null : 'Niepotwierdzone',
              account.twoFactor ? 'Logowanie dwuetapowe' : null, account.ownedTeams ? `Właściciel zespołów: ${account.ownedTeams}` : null,
              account.inactiveOwnerKept ? 'Nieaktywne od 24 miesięcy, zachowane dla zespołów' : null]
              .filter(Boolean).join(' · ') || 'Aktywne'}</td>
            <td className="operator-actions">{account.id === selfId ? <span className="field-hint">To Ty</span> : <>
              {account.blockedAt
                ? <button type="button" className="text-link" disabled={actions.unblock.isPending} onClick={() => actions.unblock.mutate(account.id)}>Odblokuj</button>
                : <button type="button" className="text-link" onClick={() => open('block', account)}>Zablokuj</button>}
              {account.twoFactor && <button type="button" className="text-link" onClick={() => open('two-factor', account)}>Wyłącz logowanie dwuetapowe</button>}
              <button type="button" className="text-link" onClick={() => open('delete', account)}>Usuń</button>
            </>}</td>
          </tr>)}</tbody>
        </table>
      </div>}
    {accounts.data && <Pager page={page} hasMore={accounts.data.hasMore} onPage={setPage} label="Strony kont" />}

    {dialog?.kind === 'block' && <Modal title="Zablokuj konto" busy={busy} onClose={close}>
      <form onSubmit={event => { event.preventDefault(); if (reason.trim()) actions.block.mutate({ id: dialog.account.id, reason }, { onSuccess: close }) }}>
        <p className="dialog-description">{dialog.account.email} zostanie wylogowane na wszystkich urządzeniach i nie zaloguje się, dopóki nie odblokujesz konta.</p>
        <div className="field-group"><label htmlFor="operator-block-reason">Powód (widoczny tylko dla operatorów)</label>
          <textarea className="text-input" id="operator-block-reason" rows={2} maxLength={500} value={reason} onChange={event => setReason(event.target.value)} /></div>
        {dialogError && <p className="form-error" role="alert">{dialogError}</p>}
        <footer className="dialog-actions"><button type="button" className="button-secondary" disabled={busy} onClick={close}>Anuluj</button>
          <button type="submit" className="button-primary button-danger" disabled={busy || !reason.trim()}>Zablokuj</button></footer>
      </form>
    </Modal>}
    {dialog?.kind === 'two-factor' && <Modal title="Wyłącz logowanie dwuetapowe" busy={busy} onClose={close}>
      <p className="dialog-description">Zrób to tylko po potwierdzeniu, że prosi o to właściciel konta {dialog.account.email}. Dostanie on wiadomość e-mail o zmianie.</p>
      {dialogError && <p className="form-error" role="alert">{dialogError}</p>}
      <footer className="dialog-actions"><button type="button" className="button-secondary" disabled={busy} onClick={close}>Anuluj</button>
        <button type="button" className="button-primary button-danger" disabled={busy} onClick={() => actions.turnOffTwoFactor.mutate(dialog.account.id, { onSuccess: close })}>Wyłącz</button></footer>
    </Modal>}
    {dialog?.kind === 'delete' && <Modal title="Usuń konto" busy={busy} onClose={close}>
      <form onSubmit={event => { event.preventDefault(); if (confirmation.trim().toLowerCase() === dialog.account.email.toLowerCase()) actions.deleteAccount.mutate(dialog.account.id, { onSuccess: close }) }}>
        <p className="dialog-description">Dane osobowe konta zostaną usunięte, a jego godziny zostaną w zespołach pod nazwą „Użytkownik usunięty”. Zespoły, których jest właścicielem, usuń najpierw. Wpisz adres konta, aby potwierdzić.</p>
        <div className="field-group"><label htmlFor="operator-delete-account">Adres e-mail konta</label>
          <input className="text-input" id="operator-delete-account" value={confirmation} onChange={event => setConfirmation(event.target.value)} /></div>
        {dialogError && <p className="form-error" role="alert">{dialogError}</p>}
        <footer className="dialog-actions"><button type="button" className="button-secondary" disabled={busy} onClick={close}>Anuluj</button>
          <button type="submit" className="button-primary button-danger" disabled={busy || confirmation.trim().toLowerCase() !== dialog.account.email.toLowerCase()}>Usuń konto</button></footer>
      </form>
    </Modal>}
  </section>
}

function TeamsSection() {
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const teams = useOperatorTeams(query, page)
  const { deleteTeam } = useOperatorActions()
  const [selected, setSelected] = useState<OperatorTeam | null>(null)
  const [confirmation, setConfirmation] = useState('')

  return <section className="overview-panel account-section" aria-labelledby="operator-teams-title">
    <h2 id="operator-teams-title">Zespoły</h2>
    <SearchForm label="Szukaj zespołu po nazwie" onSearch={value => { setQuery(value); setPage(0) }} />
    {teams.isPending ? <p role="status"><LoaderCircle size={18} className="spin" />Wczytywanie zespołów…</p>
      : teams.isError ? <div role="alert" className="dashboard-error">{teams.error.message}</div>
      : teams.data.items.length === 0 ? <p>Nie znaleziono zespołów.</p>
      : <div className="team-table-wrap" tabIndex={0} role="region" aria-label="Lista zespołów" aria-busy={teams.isFetching}>
        <table className="team-table"><caption className="sr-only">Zespoły od najnowszego</caption>
          <thead><tr><th scope="col">Zespół</th><th scope="col">Właściciel</th><th scope="col">Członkowie</th><th scope="col">Wpisy</th><th scope="col">Utworzono</th><th scope="col">Akcje</th></tr></thead>
          <tbody>{teams.data.items.map(team => <tr key={team.id}>
            <td>{team.name}{team.archivedAt && <span className="field-hint"> · zarchiwizowany</span>}</td>
            <td>{team.ownerEmail ?? '—'}</td><td>{team.members}</td><td>{team.timeEntries}</td><td>{dateLabel(team.createdAt)}</td>
            <td><button type="button" className="text-link" onClick={() => { deleteTeam.reset(); setConfirmation(''); setSelected(team) }}>Usuń</button></td>
          </tr>)}</tbody>
        </table>
      </div>}
    {teams.data && <Pager page={page} hasMore={teams.data.hasMore} onPage={setPage} label="Strony zespołów" />}
    {selected && <Modal title="Usuń zespół" busy={deleteTeam.isPending} onClose={() => setSelected(null)}>
      <form onSubmit={event => { event.preventDefault(); if (confirmation.trim() === selected.name) deleteTeam.mutate(selected.id, { onSuccess: () => setSelected(null) }) }}>
        <p className="dialog-description">Zespół, jego projekty i wszystkie wpisy czasu ({selected.timeEntries}) zostaną usunięte na stałe, także działające timery. Wpisz nazwę zespołu, aby potwierdzić.</p>
        <div className="field-group"><label htmlFor="operator-delete-team">Nazwa zespołu</label>
          <input className="text-input" id="operator-delete-team" value={confirmation} onChange={event => setConfirmation(event.target.value)} /></div>
        {deleteTeam.isError && <p className="form-error" role="alert">{deleteTeam.error.message}</p>}
        <footer className="dialog-actions"><button type="button" className="button-secondary" disabled={deleteTeam.isPending} onClick={() => setSelected(null)}>Anuluj</button>
          <button type="submit" className="button-primary button-danger" disabled={deleteTeam.isPending || confirmation.trim() !== selected.name}>Usuń zespół</button></footer>
      </form>
    </Modal>}
  </section>
}

export function OperatorPanel({ userId }: { userId: string }) {
  return <div className="account-panel">
    <StatsSection />
    <AnnouncementSection />
    <AccountsSection selfId={userId} />
    <TeamsSection />
  </div>
}
