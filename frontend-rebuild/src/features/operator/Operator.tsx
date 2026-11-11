import { useState, type FormEvent } from 'react'
import { Search, ShieldAlert } from 'lucide-react'
import { useI18n, useMessages } from '../../content/i18n'
import { useUser } from '../../app/session'
import type { OperatorAccount, OperatorTeam } from '../../lib/api'
import { useOperatorAccounts, useOperatorActions, useOperatorStats, useOperatorTeams, usePublicConfig } from '../../lib/operator'
import { Button, Dialog, Field, LoadError, Loading, Notice } from '../../ui'
import { useToast } from '../../ui/toast'
import './operator.css'

const messages = {
  pl: {
    title: 'Operator instancji', warning: 'Pracujesz z uprawnieniami operatora. Działania tutaj dotyczą wszystkich osób na tej instancji.',
    stats: 'Statystyki', accounts: 'Konta', unconfirmed: 'Niepotwierdzone', blocked: 'Zablokowane', week: 'Aktywne w 7 dni', month: 'Aktywne w 30 dni', twoFactor: 'Z logowaniem dwuetapowym', teams: 'Zespoły', entries: 'Wpisy czasu',
    announcement: 'Ogłoszenie dla wszystkich', announcementHint: 'Baner widać na każdej stronie, także przed zalogowaniem, np. przed pracami serwisowymi.', text: 'Treść ogłoszenia', level: 'Rodzaj', info: 'Informacja', warn: 'Ostrzeżenie',
    publish: 'Opublikuj', published: 'Ogłoszenie opublikowane.', clear: 'Usuń ogłoszenie', cleared: 'Ogłoszenie usunięte.',
    searchAccounts: 'Szukaj konta po adresie lub nazwie', searchTeams: 'Szukaj zespołu po nazwie', search: 'Szukaj', noAccounts: 'Nie znaleziono kont.', noTeams: 'Nie znaleziono zespołów.',
    account: 'Konto', created: 'Utworzono', active: 'Ostatnia aktywność', state: 'Stan', actions: 'Działania', you: 'To Ty', ok: 'Aktywne', blockedState: 'Zablokowane: {reason}', notConfirmed: 'Niepotwierdzone', withTwoFactor: '2FA', owns: 'Właściciel zespołów: {count}', keptOwner: 'Nieaktywne od 24 miesięcy, zachowane dla zespołów',
    block: 'Zablokuj', unblock: 'Odblokuj', unblocked: 'Konto odblokowane.', turnOff: 'Wyłącz 2FA', delete: 'Usuń', blockTitle: 'Zablokować konto?', blockBody: '{email} zostanie wylogowane na wszystkich urządzeniach i nie zaloguje się do czasu odblokowania.', reason: 'Powód (widoczny tylko dla operatorów)', blockedDone: 'Konto zablokowane.',
    twoFactorTitle: 'Wyłączyć logowanie dwuetapowe?', twoFactorBody: 'Zrób to tylko po potwierdzeniu, że prosi o to właściciel konta {email}. Dostanie wiadomość o zmianie.', twoFactorDone: 'Logowanie dwuetapowe wyłączone.',
    deleteAccount: 'Usunąć konto?', deleteAccountBody: 'Dane osobowe konta zostaną usunięte, a jego godziny zostaną w zespołach jako „Użytkownik usunięty”. Zespoły, których jest właścicielem, usuń najpierw. Wpisz adres konta, aby potwierdzić.', emailConfirm: 'Adres e-mail konta', deletedAccount: 'Konto usunięte.',
    team: 'Zespół', owner: 'Właściciel', members: 'Członkowie', archived: 'zarchiwizowany', deleteTeam: 'Usunąć zespół?', deleteTeamBody: 'Zespół, jego projekty i wszystkie wpisy czasu ({count}) zostaną usunięte na stałe, także działające timery. Wpisz nazwę zespołu, aby potwierdzić.', teamConfirm: 'Nazwa zespołu', deletedTeam: 'Zespół usunięty.',
    prev: 'Poprzednia', next: 'Następna', page: 'Strona {page}',
  },
  en: {
    title: 'Instance operator', warning: 'You are working with operator permissions. Actions here affect everyone on this instance.',
    stats: 'Statistics', accounts: 'Accounts', unconfirmed: 'Unconfirmed', blocked: 'Blocked', week: 'Active in 7 days', month: 'Active in 30 days', twoFactor: 'With two-step sign-in', teams: 'Teams', entries: 'Time entries',
    announcement: 'Announcement for everyone', announcementHint: 'The banner shows on every page, also before sign-in, e.g. before maintenance.', text: 'Announcement text', level: 'Kind', info: 'Information', warn: 'Warning',
    publish: 'Publish', published: 'Announcement published.', clear: 'Remove announcement', cleared: 'Announcement removed.',
    searchAccounts: 'Search accounts by address or name', searchTeams: 'Search teams by name', search: 'Search', noAccounts: 'No accounts found.', noTeams: 'No teams found.',
    account: 'Account', created: 'Created', active: 'Last activity', state: 'State', actions: 'Actions', you: 'You', ok: 'Active', blockedState: 'Blocked: {reason}', notConfirmed: 'Unconfirmed', withTwoFactor: '2FA', owns: 'Owns teams: {count}', keptOwner: 'Inactive for 24 months, kept for its teams',
    block: 'Block', unblock: 'Unblock', unblocked: 'Account unblocked.', turnOff: 'Turn off 2FA', delete: 'Delete', blockTitle: 'Block the account?', blockBody: '{email} is signed out everywhere and cannot sign in until unblocked.', reason: 'Reason (visible to operators only)', blockedDone: 'Account blocked.',
    twoFactorTitle: 'Turn off two-step sign-in?', twoFactorBody: 'Do this only after confirming that the owner of {email} asked for it. They get a message about the change.', twoFactorDone: 'Two-step sign-in turned off.',
    deleteAccount: 'Delete the account?', deleteAccountBody: 'Personal data is removed and its hours stay in teams as "Deleted user". Delete the teams it owns first. Type the account address to confirm.', emailConfirm: 'Account e-mail', deletedAccount: 'Account deleted.',
    team: 'Team', owner: 'Owner', members: 'Members', archived: 'archived', deleteTeam: 'Delete the team?', deleteTeamBody: 'The team, its projects and all time entries ({count}) are deleted permanently, running timers too. Type the team name to confirm.', teamConfirm: 'Team name', deletedTeam: 'Team deleted.',
    prev: 'Previous', next: 'Next', page: 'Page {page}',
  },
}

const day = (value: string | null, locale: string) => value ? new Date(value).toLocaleDateString(locale === 'pl' ? 'pl-PL' : 'en-GB', { dateStyle: 'medium' }) : '—'

/** U24: statistics, announcement and abuse handling, visibly separated from ordinary work. */
export function Operator() {
  const m = useMessages(messages)
  return <div className="page operator">
    <header className="page-head"><h1>{m('title')}</h1></header>
    <div className="operator-band" role="note"><ShieldAlert size={18} aria-hidden="true" />{m('warning')}</div>
    <Stats /><Announcement /><Accounts /><Teams />
  </div>
}

function Stats() {
  const m = useMessages(messages)
  const stats = useOperatorStats()
  if (stats.isPending) return <Loading />
  if (stats.isError) return <LoadError onRetry={() => void stats.refetch()} message={stats.error.message} />
  const items: [string, number][] = [[m('accounts'), stats.data.accounts], [m('unconfirmed'), stats.data.unconfirmed], [m('blocked'), stats.data.blocked], [m('week'), stats.data.activeLastWeek],
    [m('month'), stats.data.activeLastMonth], [m('twoFactor'), stats.data.twoFactor], [m('teams'), stats.data.teams], [m('entries'), stats.data.timeEntries]]
  return <section aria-labelledby="stats-title" className="stack"><h2 id="stats-title">{m('stats')}</h2>
    <dl className="stat-grid">{items.map(([label, value]) => <div key={label} className="stat"><dt>{label}</dt><dd className="num">{value}</dd></div>)}</dl></section>
}

function Announcement() {
  const m = useMessages(messages)
  const toast = useToast()
  const config = usePublicConfig()
  const { announce, clearAnnouncement } = useOperatorActions()
  const current = config.data?.announcement ?? null
  const [message, setMessage] = useState<string | null>(null)
  const [level, setLevel] = useState<'INFO' | 'WARNING' | null>(null)
  const text = message ?? current?.message ?? ''
  const chosen = level ?? current?.level ?? 'INFO'
  return <form className="card stack" onSubmit={event => { event.preventDefault(); if (text.trim()) announce.mutate({ message: text, level: chosen }, { onSuccess: () => { setMessage(null); setLevel(null); toast(m('published')) } }) }}>
    <h2>{m('announcement')}</h2><p className="field-hint">{m('announcementHint')}</p>
    <Field label={m('text')}>{props => <textarea {...props} className="textarea input" rows={3} maxLength={500} value={text} onChange={event => { setMessage(event.target.value); announce.reset() }} />}</Field>
    <Field label={m('level')}>{props => <select {...props} className="select input narrow" value={chosen} onChange={event => setLevel(event.target.value as 'INFO' | 'WARNING')}><option value="INFO">{m('info')}</option><option value="WARNING">{m('warn')}</option></select>}</Field>
    {(announce.isError || clearAnnouncement.isError) && <Notice tone="error">{(announce.error ?? clearAnnouncement.error)!.message}</Notice>}
    <div className="row-inline">
      <Button type="submit" variant="primary" pending={announce.isPending} disabled={!text.trim()}>{m('publish')}</Button>
      {current && <Button variant="ghost" pending={clearAnnouncement.isPending} onClick={() => { announce.reset(); clearAnnouncement.mutate(undefined, { onSuccess: () => { setMessage(null); setLevel(null); toast(m('cleared')) } }) }}>{m('clear')}</Button>}
    </div>
  </form>
}

function SearchBox({ label, onSearch }: { label: string; onSearch: (query: string) => void }) {
  const m = useMessages(messages)
  const [query, setQuery] = useState('')
  return <form role="search" className="search-row" onSubmit={(event: FormEvent) => { event.preventDefault(); onSearch(query) }}>
    <div className="input-wrap"><Search size={16} aria-hidden="true" /><input className="input" aria-label={label} placeholder={label} maxLength={100} value={query} onChange={event => setQuery(event.target.value)} /></div>
    <Button type="submit">{m('search')}</Button></form>
}

function Pager({ page, hasMore, onPage }: { page: number; hasMore: boolean; onPage: (page: number) => void }) {
  const m = useMessages(messages)
  if (page === 0 && !hasMore) return null
  return <nav className="pager" aria-label={m('page', { page: page + 1 })}><Button small disabled={page === 0} onClick={() => onPage(page - 1)}>{m('prev')}</Button><span>{m('page', { page: page + 1 })}</span>
    <Button small disabled={!hasMore} onClick={() => onPage(page + 1)}>{m('next')}</Button></nav>
}

type AccountDialog = { kind: 'block' | 'delete' | 'two-factor'; account: OperatorAccount }

function Accounts() {
  const user = useUser()
  const { t, locale } = useI18n()
  const m = useMessages(messages)
  const toast = useToast()
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const accounts = useOperatorAccounts(query, page)
  const actions = useOperatorActions()
  const [dialog, setDialog] = useState<AccountDialog | null>(null)
  const [reason, setReason] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const busy = actions.block.isPending || actions.deleteAccount.isPending || actions.turnOffTwoFactor.isPending
  const error = (dialog?.kind === 'block' ? actions.block.error : dialog?.kind === 'delete' ? actions.deleteAccount.error : actions.turnOffTwoFactor.error)?.message
  const open = (kind: AccountDialog['kind'], account: OperatorAccount) => { actions.block.reset(); actions.deleteAccount.reset(); actions.turnOffTwoFactor.reset(); setReason(''); setConfirmation(''); setDialog({ kind, account }) }
  const done = (text: string) => () => { setDialog(null); toast(text) }
  return <section className="card stack" aria-labelledby="accounts-title"><h2 id="accounts-title">{m('accounts')}</h2>
    <SearchBox label={m('searchAccounts')} onSearch={value => { setQuery(value); setPage(0) }} />
    {actions.unblock.isError && <Notice tone="error">{actions.unblock.error.message}</Notice>}
    {accounts.isPending ? <Loading /> : accounts.isError ? <LoadError onRetry={() => void accounts.refetch()} message={accounts.error.message} />
      : !accounts.data.items.length ? <p className="muted">{m('noAccounts')}</p>
      : <div className="table-scroll" role="region" aria-labelledby="accounts-title" tabIndex={0}><table className="table">
        <thead><tr><th scope="col">{m('account')}</th><th scope="col">{m('created')}</th><th scope="col">{m('active')}</th><th scope="col">{m('state')}</th><th scope="col">{m('actions')}</th></tr></thead>
        <tbody>{accounts.data.items.map(account => <tr key={account.id}>
          <td><b>{account.displayName}</b><br /><small className="muted">{account.email}</small></td><td>{day(account.createdAt, locale)}</td><td>{day(account.lastActiveAt, locale)}</td>
          <td>{[account.blockedAt ? m('blockedState', { reason: account.blockedReason ?? '' }) : null, account.confirmed ? null : m('notConfirmed'), account.twoFactor ? m('withTwoFactor') : null,
            account.ownedTeams ? m('owns', { count: account.ownedTeams }) : null, account.inactiveOwnerKept ? m('keptOwner') : null].filter(Boolean).join(' · ') || m('ok')}</td>
          <td><div className="row-inline">{account.id === user.id ? <span className="badge">{m('you')}</span> : <>
            {account.blockedAt ? <Button small disabled={actions.unblock.isPending} onClick={() => actions.unblock.mutate(account.id, { onSuccess: () => toast(m('unblocked')) })}>{m('unblock')}</Button>
              : <Button small onClick={() => open('block', account)}>{m('block')}</Button>}
            {account.twoFactor && <Button small onClick={() => open('two-factor', account)}>{m('turnOff')}</Button>}
            <Button small variant="ghost" className="danger-text" onClick={() => open('delete', account)}>{m('delete')}</Button></>}</div></td>
        </tr>)}</tbody></table></div>}
    {accounts.data && <Pager page={page} hasMore={accounts.data.hasMore} onPage={setPage} />}
    {dialog?.kind === 'block' && <Dialog open title={m('blockTitle')} onClose={() => setDialog(null)} busy={busy}>
      <form className="stack" onSubmit={event => { event.preventDefault(); if (reason.trim()) actions.block.mutate({ id: dialog.account.id, reason }, { onSuccess: done(m('blockedDone')) }) }}>
        <p>{m('blockBody', { email: dialog.account.email })}</p>
        <Field label={m('reason')}>{props => <textarea {...props} className="textarea input" rows={2} maxLength={500} value={reason} onChange={event => setReason(event.target.value)} />}</Field>
        {error && <Notice tone="error">{error}</Notice>}
        <div className="dialog-actions"><Button onClick={() => setDialog(null)} disabled={busy}>{t('common.cancel')}</Button><Button type="submit" variant="danger" pending={busy} disabled={!reason.trim()}>{m('block')}</Button></div>
      </form></Dialog>}
    {dialog?.kind === 'two-factor' && <Dialog open title={m('twoFactorTitle')} onClose={() => setDialog(null)} busy={busy}>
      <p>{m('twoFactorBody', { email: dialog.account.email })}</p>{error && <Notice tone="error">{error}</Notice>}
      <div className="dialog-actions"><Button onClick={() => setDialog(null)} disabled={busy}>{t('common.cancel')}</Button>
        <Button variant="danger" pending={busy} onClick={() => actions.turnOffTwoFactor.mutate(dialog.account.id, { onSuccess: done(m('twoFactorDone')) })}>{m('turnOff')}</Button></div></Dialog>}
    {dialog?.kind === 'delete' && <Dialog open title={m('deleteAccount')} onClose={() => setDialog(null)} busy={busy}>
      <form className="stack" onSubmit={event => { event.preventDefault(); if (confirmation.trim().toLowerCase() === dialog.account.email.toLowerCase()) actions.deleteAccount.mutate(dialog.account.id, { onSuccess: done(m('deletedAccount')) }) }}>
        <p>{m('deleteAccountBody')}</p>
        <Field label={m('emailConfirm')}>{props => <input {...props} className="input" autoComplete="off" value={confirmation} onChange={event => setConfirmation(event.target.value)} />}</Field>
        {error && <Notice tone="error">{error}</Notice>}
        <div className="dialog-actions"><Button onClick={() => setDialog(null)} disabled={busy}>{t('common.cancel')}</Button>
          <Button type="submit" variant="danger" pending={busy} disabled={confirmation.trim().toLowerCase() !== dialog.account.email.toLowerCase()}>{m('delete')}</Button></div>
      </form></Dialog>}
  </section>
}

function Teams() {
  const { t, locale } = useI18n()
  const m = useMessages(messages)
  const toast = useToast()
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const teams = useOperatorTeams(query, page)
  const { deleteTeam } = useOperatorActions()
  const [selected, setSelected] = useState<OperatorTeam | null>(null)
  const [confirmation, setConfirmation] = useState('')
  return <section className="card stack" aria-labelledby="op-teams-title"><h2 id="op-teams-title">{m('teams')}</h2>
    <SearchBox label={m('searchTeams')} onSearch={value => { setQuery(value); setPage(0) }} />
    {teams.isPending ? <Loading /> : teams.isError ? <LoadError onRetry={() => void teams.refetch()} message={teams.error.message} />
      : !teams.data.items.length ? <p className="muted">{m('noTeams')}</p>
      : <div className="table-scroll" role="region" aria-labelledby="op-teams-title" tabIndex={0}><table className="table">
        <thead><tr><th scope="col">{m('team')}</th><th scope="col">{m('owner')}</th><th scope="col">{m('members')}</th><th scope="col">{m('entries')}</th><th scope="col">{m('created')}</th><th scope="col">{m('actions')}</th></tr></thead>
        <tbody>{teams.data.items.map(team => <tr key={team.id}><td>{team.name}{team.archivedAt && <small className="muted"> · {m('archived')}</small>}</td><td>{team.ownerEmail ?? '—'}</td>
          <td className="num">{team.members}</td><td className="num">{team.timeEntries}</td><td>{day(team.createdAt, locale)}</td>
          <td><Button small variant="ghost" className="danger-text" onClick={() => { deleteTeam.reset(); setConfirmation(''); setSelected(team) }}>{m('delete')}</Button></td></tr>)}</tbody></table></div>}
    {teams.data && <Pager page={page} hasMore={teams.data.hasMore} onPage={setPage} />}
    {selected && <Dialog open title={m('deleteTeam')} onClose={() => setSelected(null)} busy={deleteTeam.isPending}>
      <form className="stack" onSubmit={event => { event.preventDefault(); if (confirmation.trim() === selected.name) deleteTeam.mutate(selected.id, { onSuccess: () => { setSelected(null); toast(m('deletedTeam')) } }) }}>
        <p>{m('deleteTeamBody', { count: selected.timeEntries })}</p>
        <Field label={m('teamConfirm')}>{props => <input {...props} className="input" autoComplete="off" value={confirmation} onChange={event => setConfirmation(event.target.value)} />}</Field>
        {deleteTeam.isError && <Notice tone="error">{deleteTeam.error.message}</Notice>}
        <div className="dialog-actions"><Button onClick={() => setSelected(null)} disabled={deleteTeam.isPending}>{t('common.cancel')}</Button>
          <Button type="submit" variant="danger" pending={deleteTeam.isPending} disabled={confirmation.trim() !== selected.name}>{m('delete')}</Button></div>
      </form></Dialog>}
  </section>
}
