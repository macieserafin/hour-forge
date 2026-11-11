import { useState } from 'react'
import { CheckCircle2, ExternalLink, Plug, Unplug } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'
import { useI18n, useMessages } from '../../content/i18n'
import { useUser } from '../../app/session'
import { useDisconnectJiraAccount, useDisconnectJiraSite, useJiraSites, useJiraStatus, useSelectJiraSite, useStartJira } from '../../lib/jira'
import { Button, Dialog, LoadError, Loading, Notice } from '../../ui'
import { useTeamContext } from './Teams'

const messages = {
  pl: {
    title: 'Jira Cloud', scope: 'HourForge tylko odczytuje listę zadań (zakres read:jira-work). Niczego nie zapisuje w Jirze. Produkt działa także bez Jiry.',
    off: 'Integracja z Jira jest wyłączona na tym serwerze.', noSite: 'Zespół nie ma jeszcze połączonej witryny Jira.', connectSite: 'Połącz zespół z Jira Cloud', ownerOnly: 'Witrynę łączy właściciel lub administrator zespołu.',
    site: 'Witryna', mine: 'Twoje konto Jira', connected: 'Połączone', notConnected: 'Niepołączone', connectMine: 'Połącz moje konto Jira', disconnectMine: 'Rozłącz moje konto Jira',
    change: 'Zmień witrynę', disconnect: 'Odłącz witrynę', choose: 'Wybierz witrynę Jira zespołu', use: 'Użyj tej witryny', hide: 'Ukryj',
    disconnectTitle: 'Odłączyć witrynę Jira?', disconnectBody: 'Zespół {team} przestanie korzystać z Jiry, a połączenia kont wszystkich członków z tą witryną zostaną usunięte. Zapisany czas się nie zmieni, ale jego powiązania z zadaniami Jira zostaną usunięte.',
    connected_msg: 'Konto Jira zostało połączone.', 'choose-site': 'Konto Jira zostało połączone. Wybierz witrynę, z której korzysta zespół.', denied: 'Połączenie z Jira nie zostało zatwierdzone. Możesz spróbować ponownie.',
    'invalid-state': 'To połączenie wygasło lub zostało już użyte. Rozpocznij je jeszcze raz.', forbidden: 'Nie masz już uprawnień do tej operacji w zespole.', archived: 'Zespół jest zarchiwizowany, więc jego witryna Jira pozostaje bez zmian.',
    'no-access': 'Twoje konto Jira nie ma dostępu do witryny tego zespołu. Zaloguj się na właściwe konto Jira.', unavailable: 'Jira jest teraz niedostępna. Spróbuj ponownie za chwilę.',
  },
  en: {
    title: 'Jira Cloud', scope: 'HourForge only reads the issue list (read:jira-work scope). It writes nothing to Jira. The product also works without Jira.',
    off: 'Jira integration is turned off on this server.', noSite: 'The team has no connected Jira site yet.', connectSite: 'Connect the team to Jira Cloud', ownerOnly: 'The owner or an admin of the team connects the site.',
    site: 'Site', mine: 'Your Jira account', connected: 'Connected', notConnected: 'Not connected', connectMine: 'Connect my Jira account', disconnectMine: 'Disconnect my Jira account',
    change: 'Change site', disconnect: 'Disconnect site', choose: "Choose the team's Jira site", use: 'Use this site', hide: 'Hide',
    disconnectTitle: 'Disconnect the Jira site?', disconnectBody: '{team} stops using Jira and all members\' account connections to this site are removed. Recorded time stays, but its links to Jira issues are removed.',
    connected_msg: 'The Jira account is connected.', 'choose-site': 'The Jira account is connected. Choose the site the team uses.', denied: 'The Jira connection was not approved. You can try again.',
    'invalid-state': 'This connection expired or was already used. Start it again.', forbidden: 'You no longer have permission for this in the team.', archived: 'The team is archived, so its Jira site stays unchanged.',
    'no-access': "Your Jira account has no access to this team's site. Sign in to the right Jira account.", unavailable: 'Jira is unavailable now. Try again shortly.',
  },
}

type Outcome = 'connected' | 'choose-site' | 'denied' | 'invalid-state' | 'forbidden' | 'archived' | 'no-access' | 'unavailable'
const outcomes: Outcome[] = ['connected', 'choose-site', 'denied', 'invalid-state', 'forbidden', 'archived', 'no-access', 'unavailable']

/** U19: the team's Jira site (managers) and the person's own connection; the consent flow returns here with ?jira=. */
export function TeamJira() {
  const { team } = useTeamContext()
  const user = useUser()
  const m = useMessages(messages)
  const status = useJiraStatus(user.id, team.id)
  const start = useStartJira(team.id)
  const disconnectMine = useDisconnectJiraAccount(user.id, team.id)
  const [search, setSearch] = useSearchParams()
  const [confirm, setConfirm] = useState(false)
  const raw = search.get('jira')
  const outcome = outcomes.find(item => item === raw)
  const clear = () => setSearch(previous => { const next = new URLSearchParams(previous); next.delete('jira'); return next }, { replace: true })
  if (status.isPending) return <Loading />
  if (status.isError) return <LoadError onRetry={() => void status.refetch()} message={status.error.message} />
  const jira = status.data
  const archived = !!team.archivedAt
  const choosing = outcome === 'choose-site' && jira.canManage && !jira.siteConnected
  const good = outcome === 'connected' || outcome === 'choose-site'
  return <section className="card stack" aria-labelledby="jira-title">
    <div className="card-head"><h2 id="jira-title"><Plug size={18} aria-hidden="true" />{m('title')}</h2></div>
    <p className="muted">{m('scope')}</p>
    {outcome && <Notice tone={good ? 'success' : 'error'} action={!choosing ? <div><button type="button" className="link-btn" onClick={clear}>{m('hide')}</button></div> : undefined}>
      {m(outcome === 'connected' ? 'connected_msg' : outcome)}</Notice>}
    {!jira.available ? <p>{m('off')}</p>
      : choosing ? <SiteChooser onChosen={clear} />
      : !jira.siteConnected ? <>
        <p>{m('noSite')}</p>
        {jira.canManage && !archived ? <Button variant="primary" pending={start.isPending} onClick={() => start.mutate('SITE')}>{m('connectSite')}</Button> : <p className="field-hint">{m('ownerOnly')}</p>}
      </> : <>
        <dl className="facts">
          <div><dt>{m('site')}</dt><dd>{jira.siteName} {jira.siteUrl?.startsWith('https://') && <a className="text-link" href={jira.siteUrl} target="_blank" rel="noopener noreferrer">{jira.siteUrl}<ExternalLink size={13} aria-hidden="true" /></a>}</dd></div>
          <div><dt>{m('mine')}</dt><dd>{jira.userConnected ? <span className="ok-text"><CheckCircle2 size={15} aria-hidden="true" />{m('connected')}</span> : m('notConnected')}</dd></div>
        </dl>
        <div className="row-inline">
          {jira.userConnected ? <Button pending={disconnectMine.isPending} onClick={() => disconnectMine.mutate()}><Unplug size={16} aria-hidden="true" />{m('disconnectMine')}</Button>
            : <Button variant="primary" pending={start.isPending} onClick={() => start.mutate('MEMBER')}>{m('connectMine')}</Button>}
          {jira.canManage && !archived && <><Button onClick={() => start.mutate('SITE')} disabled={start.isPending}>{m('change')}</Button><Button variant="ghost" onClick={() => setConfirm(true)}>{m('disconnect')}</Button></>}
        </div>
      </>}
    {(start.isError || disconnectMine.isError) && <Notice tone="error">{(start.error ?? disconnectMine.error)!.message}</Notice>}
    {confirm && <DisconnectDialog onClose={() => setConfirm(false)} />}
  </section>
}

function SiteChooser({ onChosen }: { onChosen: () => void }) {
  const { team } = useTeamContext()
  const user = useUser()
  const m = useMessages(messages)
  const sites = useJiraSites(user.id, team.id, true)
  const select = useSelectJiraSite(user.id, team.id)
  const [chosen, setChosen] = useState('')
  if (sites.isPending) return <Loading />
  if (sites.isError) return <LoadError onRetry={() => void sites.refetch()} message={sites.error.message} />
  return <form className="stack" onSubmit={event => { event.preventDefault(); if (chosen) select.mutate(chosen, { onSuccess: onChosen }) }}>
    <fieldset className="stack-sm field" disabled={select.isPending}><legend className="field-label">{m('choose')}</legend>
      {sites.data.map(site => <label key={site.cloudId} className="choice-row"><input type="radio" name="jira-site" checked={chosen === site.cloudId} onChange={() => setChosen(site.cloudId)} />
        <span><b>{site.name}</b><small className="muted"> {site.url}</small></span></label>)}</fieldset>
    {select.isError && <Notice tone="error">{select.error.message}</Notice>}
    <Button type="submit" variant="primary" pending={select.isPending} disabled={!chosen}>{m('use')}</Button>
  </form>
}

function DisconnectDialog({ onClose }: { onClose: () => void }) {
  const { team } = useTeamContext()
  const user = useUser()
  const { t } = useI18n()
  const m = useMessages(messages)
  const disconnect = useDisconnectJiraSite(user.id, team.id)
  return <Dialog open title={m('disconnectTitle')} onClose={onClose} busy={disconnect.isPending}>
    <p>{m('disconnectBody', { team: team.name })}</p>
    {disconnect.isError && <Notice tone="error">{disconnect.error.message}</Notice>}
    <div className="dialog-actions"><Button onClick={onClose} disabled={disconnect.isPending}>{t('common.cancel')}</Button>
      <Button variant="danger" pending={disconnect.isPending} onClick={() => disconnect.mutate(undefined, { onSuccess: onClose })}><Unplug size={16} aria-hidden="true" />{m('disconnect')}</Button></div>
  </Dialog>
}
